package com.d_d;/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.2.
 */

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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

        processQueryFile(parameters.get("queryFilePath"), model, parameters.get("trecEvalOutputPath"));

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

    /**
     * Process the query file.
     *
     * @param queryFilePath
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath,
                                 RetrievalModel model, String outputFilePath)
            throws IOException {

        BufferedReader input = null;
        BufferedWriter output = null;

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));
            output = new BufferedWriter(new FileWriter(outputFilePath));

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

                r = processQuery(query, model);

//                if (r != null) {
//                    printResults(qid, r);
//                    System.out.println();
//                }

                if (r != null) {
                    int i = 0;
                    for (i = 0; i < r.size(); i ++) {
                        if (i == 100) break;
                        output.write(String.format("%s  Q0  %s  %d  %.18f  fubar\n", qid, Idx.getExternalDocid(r.getDocid(i)), i + 1, r.getDocidScore(i)));
                        //System.out.println(String.format("%s  Q0  %s  %d  %f  run-1", qid, Idx.getExternalDocid(r.getDocid(i)), i, r.getDocidScore(i)));
                    }
                    if (i == 0) {
                        output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummyRecord", 1, 0));
                    }
                } else {
                    output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummy", 1, 0));
                    //System.out.println(String.format("%s  Q0  %s  %d  %f  run-1", qid, "dummy", 1, 0));
                }


            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            output.close();
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
