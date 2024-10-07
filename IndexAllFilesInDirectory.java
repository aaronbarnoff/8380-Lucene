import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class IndexAllFilesInDirectory {
    static int counter = 0;

    public static void main(String[] args) throws Exception {
        String indexPath = "E:\\IR Project\\citeseer2_index"; // \\hamlet_index"; \\paperTitles_index";
        String docsPath = "E:\\IR Project\\citeseer2";        // \\hamletTest"; \\dlbp_title.txt";

        System.out.println("Indexing to directory '" + indexPath + " '...");
        Directory dir = FSDirectory.open(Paths.get(indexPath));

        String stopFileLocation = "E:\\IR Project";
        Path stopFilePath = Paths.get(stopFileLocation);
        Analyzer analyzer = CustomAnalyzer.builder(stopFilePath)    //using custom analyzer to help with phrase searches
                .withTokenizer("standard")
                .addTokenFilter("lowercase")                        //i tried disabling lowercase to allow matching by case but not too helpful
                /*
                .addTokenFilter("ngram",                            //use ngram filter on index; didn't seem to help in my case
                        "minGramSize", "2",                         //avoid indexing single character words
                        "maxGramSize", "3",
                        "preserveOriginal","true")                  //keep the original token as well
                 */
                //.addTokenFilter("snowballPorter")                 //I didn't find stemmers were helpful for my phrase searching
                .addTokenFilter("stop",                             //remove stop words so i can do exact phrase matching
                        "ignoreCase", "true",                       //key-value pairs
                        "words", "myStopWordsEmpty.txt",
                        "format", "wordset")                        //single word per line
                .build();

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        //iwc.setSimilarity(new ClassicSimilarity());               //the similarity implementation of indexer and reader should always match
        IndexWriter writer = new IndexWriter(dir, iwc);
        indexDocs(writer, Paths.get(docsPath));
        writer.close();
    }

    public static String readBufferedReader(BufferedReader br) throws IOException {
        StringWriter writer = new StringWriter();
        char[] buffer = new char[1024];
        int numRead;
        while ((numRead = br.read(buffer)) != -1) {
            writer.write(buffer, 0, numRead);
        }
        return writer.toString();
    }

    static void indexDoc(IndexWriter writer, Path file) throws Exception {
        FieldType fullField = new FieldType();
        fullField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);  //for my phrase search
        fullField.setStored(true);
        fullField.setTokenized(true);
        fullField.setOmitNorms(false);
        fullField.setStoreTermVectors(true);         //store vectors so i can return the term that was hit (highlighter)
        fullField.setStoreTermVectorPositions(true);
        fullField.setStoreTermVectorOffsets(true);

        InputStream stream = Files.newInputStream(file);

        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String title = br.readLine();

        Document doc = new Document();
        doc.add(new StringField("path", file.toString(), Field.Store.YES));

        doc.add(new Field("title", title, fullField));                          //using my custom field type
        doc.add(new Field("contents", readBufferedReader(br), fullField));

        writer.addDocument(doc);
        counter++;
        if (counter % 1000 == 0)
            System.out.println("indexing " + counter + "-th file " + file.getFileName());
    }

    static void indexDocs(final IndexWriter writer , Path path) throws Exception {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    indexDoc(writer, file);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}