import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class IndexSingleFile {
    public static void main(String[] args) throws Exception {
        String indexPath = "E:\\IR Project\\paperTitles_index";
        String filePath = "E:\\IR Project\\dblp_title.txt";

        System.out.println("Indexing to directory '" + indexPath + " '...");
        Directory dir = FSDirectory.open(Paths.get(indexPath));

        String stopFileLocation = "E:\\IR Project";
        Path stopFilePath = Paths.get(stopFileLocation);
        Analyzer analyzer = CustomAnalyzer.builder(stopFilePath)
                .withTokenizer("standard")
                .addTokenFilter("lowercase")
                .addTokenFilter("stop",
                        "ignoreCase", "true",
                        "words", "myStopWordsEmpty.txt",
                        "format", "wordset")
                .build();

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(dir, iwc);
        indexFile(writer, Paths.get(filePath));
        writer.close();
    }

    static void indexFile(IndexWriter writer, Path file) throws Exception {
        InputStream stream = Files.newInputStream(file);

        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        FieldType fullField = new FieldType();
        fullField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        fullField.setStored(true);
        fullField.setTokenized(true);
        fullField.setOmitNorms(false);
        fullField.setStoreTermVectors(true);
        fullField.setStoreTermVectorPositions(true);
        fullField.setStoreTermVectorOffsets(true);

        String title;
        while ((title = br.readLine()) != null){
            Document doc = new Document();
            Field contents = new Field("title", title, fullField);
            doc.add(contents);
            writer.addDocument(doc);
        }
    }
}