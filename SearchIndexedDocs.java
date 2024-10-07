import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import javax.swing.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.exit;

public class SearchIndexedDocs {
    private IndexSearcher searcher;
    private IndexReader reader;
    private StoredFields storedFields; //for more efficient paging (not re-querying each page). not implemented

    public SearchIndexedDocs(String index) throws Exception {
        Directory indexDir = FSDirectory.open(Paths.get(index));
        reader = DirectoryReader.open(indexDir);
        searcher = new IndexSearcher(reader);                 //current Lucene uses BM25 similarity by default
        //searcher.setSimilarity(new ClassicSimilarity());    // Can use TF-IDF similarity instead, but they recommend BM25
        IndexSearcher.setMaxClauseCount(65536);               //up from 1024, can be slow; wilcards create many clauses
    }

    public Query basicSearch(String userQuery, String field) throws Exception {
        Analyzer regularAnalyzer = new StandardAnalyzer();

        /*
        Analyzer regularAnalyzer = CustomAnalyzer.builder()
                .withTokenizer("standard")
                .addTokenFilter("lowercase")
                .addTokenFilter("snowballPorter") //stemmers don't seem as helpful for my features
                .build();
         */
        QueryParser parser = new QueryParser(field, regularAnalyzer);
        Query query = parser.parse(userQuery);  //this uses lucene's built in parser syntax eg "search string here"~1 for exact phrase
        return query;
    }

    public void search(String userQuery, DefaultListModel<String> listModel, String searchField, int currentPage, int resultsPerPage, int curOption, Boolean matchCase) throws Exception {
        int newPage = currentPage * resultsPerPage;
        Query query = null;
        System.out.println(curOption);
        if (userQuery.equals("")) {
            listModel.addElement("Search string is empty.");
            return;
        }
        userQuery = userQuery.toLowerCase(); //to match the analyzer's lowerCase filter used in index builder
        switch (curOption) {
            case 0:
                query = basicSearch(userQuery, searchField);
                break;
            case 1:
                if (!userQuery.contains(" ")) {
                    listModel.addElement("Phrase search requires two words.");
                    return;
                }
                query = partialPhrase(userQuery, searchField);
                break;
            case 2:
                if (!userQuery.contains(" ")) {
                    listModel.addElement("Phrase search requires two words.");
                    return;
                }
                query = wildPhrase(userQuery, searchField);
                break;
            case 3:
                if (!userQuery.contains(" ")) {
                    listModel.addElement("Phrase search requires two words.");
                    return;
                }
                query = gapPhrase(userQuery, searchField);
                break;
            case 4:
                if (!userQuery.contains(" ? ")) {
                    listModel.addElement("Invalid search.");
                    return;
                }
                query = knownGapPhrase(userQuery, searchField);
                break;
        }
        if (query == null) {
            System.out.println("Problem.");
            exit(-1);
        }

        System.out.println(query);
        TopDocs results = null;

        try {
            results = searcher.search(query, newPage + resultsPerPage);
        } catch (
                IndexSearcher.TooManyClauses e) {         //seems to trigger when using wildcard on a simple prefix/suffix e.g. "a* or *a"
            System.out.println("Too many clauses.");
            listModel.addElement("Error: Too many clauses");
            return;
        }

        ScoreDoc[] hits = results.scoreDocs;
        System.out.println(results.totalHits + " total matching documents");
        TermVectors termVectors = reader.termVectors();

        if (results.totalHits.value == 0)
            System.out.println("No results found");
        else {
            for (int i = newPage; i < Math.min(hits.length, newPage + resultsPerPage); i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                System.out.println((i + 1) + ". " + path);
                String title = doc.get("title").replaceAll("[^a-zA-Z0-9 ]", "").replaceAll("\\s+", " ").trim(); //trying to clean up the title text a bit
                if (title != null) {
                    listModel.addElement(String.format("%d. Title: ", i + 1) + title);
                } else {
                    listModel.addElement("Title: No title");
                }
                String content = doc.get(searchField);
                if (doc.get(searchField) != null) {

                    Fields vector = termVectors.get(hits[i].doc);   //this is all to report exactly which terms in the search results were hit
                    QueryScorer s = new QueryScorer(query);
                    Formatter f = new SimpleHTMLFormatter("<font style=\"color:red\">","</font>");
                    Highlighter h = new Highlighter(f, s);
                    TokenStream ts = TokenSources.getTermVectorTokenStreamOrNull(searchField, vector, h.getMaxDocCharsToAnalyze() - 1);
                    String fragment = h.getBestFragment(ts, content);

                    if (fragment != null) {
                        listModel.addElement("<html>    Found: " + fragment + "</html>");
                    }
                }
                else {
                    listModel.addElement("{Searchfield is empty.}");
                }
                listModel.addElement(""); //newline
            }
        }
    }

    //gapPhrase: (proximity search) keep order but possibly missing one word somewhere
    // e.g. "The quick fox jumped" --> "The quick [brown] fox jumped"
    private static Query gapPhrase(String userQuery, String field) {
        String[] userWords = userQuery.split("\\s+");
        SpanNearQuery.Builder sqBuilder = SpanNearQuery.newOrderedNearQuery(field);         //must be in order
        sqBuilder.setSlop(1);                                                               //single-word gap

        for (int i = 0; i < userWords.length; i++) {
            Term t = new Term(field, userWords[i]);
            sqBuilder.addClause(new SpanTermQuery(t));                                      //build the phrase query from each term in the word combination
        }
        Query sq = sqBuilder.build();

        Query result = sq;
        return result;
    }

    //knownGapPhrase: keep order but missing one word in a known spot
    //e.g. "The quick ? fox" --> "The quick [brown] fox"
    private static Query knownGapPhrase(String userQuery, String field) {
        String[] userWords = userQuery.split(" \\? ");    //split phrase [A B C ? D] into [A B C] ? [D] with 1-word gap at ?
        System.out.println(Arrays.toString(userWords));

        SpanNearQuery.Builder sqBuilder1 = SpanNearQuery.newOrderedNearQuery(field);
        sqBuilder1.setSlop(0);
        String[] firstHalf = userWords[0].split("\\s+");
        for (int i = 0; i < firstHalf.length; i++){
            Term t = new Term(field, firstHalf[i]);
            sqBuilder1.addClause(new SpanTermQuery(t));         //construct [A B]
        }
        sqBuilder1.addGap(1);                                   //insert the ? here with a 1-word gap
        String[] secondHalf = userWords[1].split("\\s+");
        for (int i = 0; i < secondHalf.length; i++){
            Term t = new Term(field, secondHalf[i]);
            sqBuilder1.addClause(new SpanTermQuery(t));         //construct [C D]
        }
        Query sq = sqBuilder1.build();                          //build [A B] ? [C D]

        Query result = sq;
        return result;
    }


    //wildPhrase: This is to allow wildcards to be used in a phrase (ordered word) search
    //e.g. "The qui* ?rown fox" --> "The qui[ck] [b]rown fox"
    private static Query wildPhrase(String userQuery, String searchField) {
        SpanNearQuery.Builder sqBuilder = new SpanNearQuery.Builder(searchField, true);     //true:ordered
        sqBuilder.setSlop(0);                                                               //slop(0): exact order

        String[] userWords = userQuery.split("\\s+");                                       //maybe combine adjacent non-* words into phrase?
        for (int i = 0; i < userWords.length; i++) {
            WildcardQuery wcQuery = new WildcardQuery(new Term(searchField, userWords[i])); //each word in phrase is turned into wildcard query
            SpanQuery sq = new SpanMultiTermQueryWrapper<>(wcQuery);                        //then it is added to the multi-term query
            sqBuilder.addClause(sq);
        }
        Query result = sqBuilder.build();
        System.out.println(result);
        return result;
    }

    //partialPhrase: This can help if the searched phrase has extra words.
    //e.g. "The very quick brown fox" --> "[the] [quick brown fox]"
    private static Query partialPhrase(String userQuery, String field) {
        BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();

        String[] userWords = userQuery.split("\\s+");
        List<String> rankList = Arrays.stream(userWords).toList();

        List<List<String>> phraseCombs = getStringCombinations(userWords);             //get all combinations of order-preserving phrase words
        for (int i = 0; i < phraseCombs.size(); i++) {
            SpanNearQuery.Builder sqBuilder = new SpanNearQuery.Builder(field, true);  //phrase query; true: must be in order
            sqBuilder.setSlop(0);                                                      //want exact order
            List<String> phraseComb = phraseCombs.get(i);

            int ranking = 0;
            for (int j = 0; j < phraseComb.size(); j++){
                String word = phraseComb.get(j);
                Term t = new Term(field, word);
                sqBuilder.addClause(new SpanTermQuery(t));                             //build the phrase query from each term in the word combination

                int rank = (int) (Math.pow((rankList.size() - rankList.indexOf(word)) + 5,2));
                ranking += rank;                                                       //weight the partial queries higher the closer their words are to the start

            }
            Query sq = sqBuilder.build();

            BoostQuery bq = new BoostQuery(sq, ranking);
            bqBuilder.add(bq, BooleanClause.Occur.SHOULD);
        }
        Query result = bqBuilder.build();
        return result;
    }

    //recursively find all order-preserving permutations of the user's query; e.g "a b c d", "a b c", "a c d", "c d", etc
    private static List<List<String>> getStringCombinations(String[] stringList) {
        List<List<String>> result = new ArrayList<>();

        findCombinations(stringList, 0, new ArrayList<>(), result);
        //System.out.println(result);
        return result;
    }

    private static void findCombinations(String[] strings, int start, List<String> current, List<List<String>> result) {
        if (current.size() >= 2) {                      //dont include empty or 1-word phrases
            result.add(new ArrayList<>(current));
            //System.out.println(current);
        }

        for (int i = start; i < strings.length; i++) {
            current.add(strings[i]);
                findCombinations(strings, i + 1, current, result);
                current.remove(current.size() - 1);
        }
    }
}

