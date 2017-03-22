/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.2.
 */

import java.io.*;
import java.util.*;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink"};


    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        //  Open the index and initialize the retrieval model.
        for (Map.Entry<String, String> x: parameters.entrySet()){
            System.out.println(x.getKey() + " : " + x.getValue());
        }

        Idx.open(parameters.get("indexPath"));
        RetrievalModel model = initializeRetrievalModel(parameters);

        //  Perform experiments.

        processQueryFile(parameters, model);

        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelString.equals("bm25")) {
            // Add BM25 and initialize it.
            double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
            double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
            double b = Double.parseDouble(parameters.get("BM25:b"));
            model = new RetrievalModelBM25(k_1, k_3, b);
        } else if (modelString.equals("indri")) {
            // Add Indri and initialize it.
            double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
            double mu = Double.parseDouble(parameters.get("Indri:mu"));
            model = new RetrievalModelIndri(lambda, mu);
        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        // Show the query that is evaluated

        System.out.println("    --> " + q);

        if (q != null) {

            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {

                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }

            r.sort();

            return r;
        } else
            return null;
    }

    private static Map<String, Double> sortByValue(Map<String, Double> unsortMap) {
        List<Map.Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return Double.compare(o2.getValue(), o1.getValue());
            }
        });

        Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
        for (Iterator<Map.Entry<String, Double>> it = list.iterator(); it.hasNext(); ) {
            Map.Entry<String, Double> entry = it.next();
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    static String expandQuery(ScoreList docIds, int fbDocs, int fbTerms, int fbMu) throws IOException {
        String expandedQuery = null;

        Map<String, Double> term_p_t_C = new HashMap<>();
        Map<String, ArrayList<Integer>> termDocIdList = new HashMap<>();
        Map<String, Double> candidateTerms = new TreeMap<>();
        double lenOfC = Idx.getSumOfFieldLengths("body");

        for (int i = 0; i < docIds.size() && i < fbDocs; i ++) {
            int docId = docIds.getDocid(i);
            double docScore = docIds.getDocidScore(i);
            double docLen = Idx.getFieldLength("body", docId);

            TermVector termVector = new TermVector(docId, "body");

            for (int j = 1; j < termVector.stemsLength(); j ++) {
                String term = termVector.stemString(j);

                if (term.contains(".") || term.contains(",")) {
                    continue;
                }

                double p_t_C = termVector.totalStemFreq(j) / lenOfC;
                term_p_t_C.put(term, p_t_C);

                double p_t_d = (((double) termVector.stemFreq(j)) + fbMu * p_t_C) / (docLen + fbMu);
                double currentDocTermScore = p_t_d * docScore * Math.log(1.0 / p_t_C);
                double score;

                if (candidateTerms.containsKey(term)) {
                    score = candidateTerms.get(term);
                    score += currentDocTermScore;
                } else {
                    score = currentDocTermScore;
                }
                candidateTerms.put(term, score);

                if (termDocIdList.containsKey(term)) {
                    ArrayList<Integer> docIdList = termDocIdList.get(term);
                    docIdList.add(docId);
                    termDocIdList.put(term, docIdList);
                } else {
                    ArrayList<Integer> docIdList = new ArrayList<>();
                    docIdList.add(docId);
                    termDocIdList.put(term, docIdList);
                }
            }
        }

        for (String term: candidateTerms.keySet()) {
            ArrayList<Integer> docIdList = termDocIdList.get(term);
            for (int i = 0; i < docIds.size() && i < fbDocs; i ++) {
                int docId = docIds.getDocid(i);
                double docScore = docIds.getDocidScore(i);
                double docLen = Idx.getFieldLength("body", docId);

                if (docIdList.contains(docId)) {
                    continue;
                }

                TermVector termVector = new TermVector(docId, "body");
                double p_t_C = term_p_t_C.get(term);
                double p_t_d = (fbMu * p_t_C) / (docLen + fbMu);
                double currentDocTermScore = p_t_d * docScore * Math.log(1.0 / p_t_C);
                double score;

                if (candidateTerms.containsKey(term)) {
                    score = candidateTerms.get(term);
                    score += currentDocTermScore;
                } else {
                    score = currentDocTermScore;
                }
                candidateTerms.put(term, score);
            }
        }

        candidateTerms = sortByValue(candidateTerms);

        Set candidateTermsSet = candidateTerms.entrySet();

        Iterator it = candidateTermsSet.iterator();

        expandedQuery = "#wand ( ";
        int i = 0;

        while (it.hasNext()) {
            Map.Entry candidateTerm = (Map.Entry) it.next();

            String term = (String) candidateTerm.getKey();
            String weight = String.format("%.4f", (Double) candidateTerm.getValue());

            expandedQuery += (" " + weight + " " + term);

            if (i >= fbTerms - 1) {
                break;
            }
            i ++;
        }

        expandedQuery += " )";

        return expandedQuery;
    }

    /**
     * Process the query file.
     *
     * @param parameters
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(Map<String, String> parameters, RetrievalModel model)
            throws Exception {

        String queryFilePath = parameters.get("queryFilePath");
        String outputFilePath = parameters.get("trecEvalOutputPath");

        boolean fb = Boolean.valueOf(parameters.get("fb"));
        String fbInitialRankingFile = parameters.get("fbInitialRankingFile");
        String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");

        BufferedReader input = null;
        BufferedWriter output = null;

        BufferedReader fbInitialRankingFileReader = null;
        BufferedWriter queryWriter = null;

        Map<String, ScoreList> fbDocId = null;

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));
            output = new BufferedWriter(new FileWriter(outputFilePath));

            if (fbExpansionQueryFile != null)
                queryWriter = new BufferedWriter(new FileWriter(fbExpansionQueryFile));

            fbDocId = new HashMap<>();

            if (fbInitialRankingFile != null) {
                fbInitialRankingFileReader = new BufferedReader(new FileReader(fbInitialRankingFile));
                String rLine = null;
                while ((rLine = fbInitialRankingFileReader.readLine()) != null) {
                    String[] rLineComponents = rLine.split(" ");
                    String qid = rLineComponents[0];
                    String eid = rLineComponents[2];
                    Double score = Double.parseDouble(rLineComponents[4]);
                    int iid = Idx.getInternalDocid(eid);
                    if (fbDocId.containsKey(qid)) {
                        fbDocId.get(qid).add(iid, score);
                    } else {
                        ScoreList r = new ScoreList();
                        r.add(iid, score);
                        fbDocId.put(qid, r);
                    }
                }
            }

            //  Each pass of the loop processes one query.

            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;
                ScoreList docIds = null;

                if (fb) {
                    int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
                    int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
                    int fbMu = Integer.parseInt(parameters.get("fbMu"));
                    double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
                    if (fbInitialRankingFile != null) {
                        docIds = fbDocId.get(qid);
                    } else {
                        docIds = processQuery(query, model);
                    }
                    String defaultOp = model.defaultQrySopName();
                    query = defaultOp + "(" + query + ")";

                    String expandedQuery = expandQuery(docIds, fbDocs, fbTerms, fbMu);

                    queryWriter.write(String.format("%s: %s\n", qid, expandedQuery));
                    queryWriter.flush();

                    query = String.format("#wand ( %.4f %s %.4f %s )", fbOrigWeight, query, 1 - fbOrigWeight, expandedQuery);
                }

                r = processQuery(query, model);

                int i;
                for (i = 0; i < r.size(); i ++) {
                    if (i == 100) break;
                    output.write(String.format("%s  Q0  %s  %d  %.12f  run-1\n", qid, Idx.getExternalDocid(r.getDocid(i)), i + 1, r.getDocidScore(i)));
                    //System.out.println(String.format("%s  Q0  %s  %d  %f  run-1", qid, Idx.getExternalDocid(r.getDocid(i)), i, r.getDocidScore(i)));
                }
                if (i == 0) {
                    output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummy", 1, 0));
                }


            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            output.close();
            //queryWriter.close();
        }
    }

    /**
     * Print the query results.
     * <p>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, ScoreList result) throws IOException {

        System.out.println(queryName + ":  ");
        if (result.size() < 1) {
            System.out.println("\tNo results.");
        } else {
            for (int i = 0; i < result.size(); i++) {
                System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
                        + result.getDocidScore(i));
            }
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath") &&
                parameters.containsKey("retrievalAlgorithm"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        return parameters;
    }

}
