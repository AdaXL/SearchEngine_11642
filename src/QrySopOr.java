/**
 * Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the OR operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score, the max value of all argument against document.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        double max = Double.MIN_VALUE;

        // Using the smallest matched doc id among all #OR operator's arguments.
        int docid = this.docIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i++) {
            // Iterate through them.
            Qry q_i = this.args.get(i);
            if (q_i.docIteratorHasMatchCache()){
                // If any argument has matching doc id and has higher score, get it and make it current maximum.
                int matchid = q_i.docIteratorGetMatch();
                if (matchid == docid){
                    double tf = ((QrySop) this.args.get(i)).getScore(r);
                    max = max < tf ? tf : max;
                }
            }
        }
        return max;
    }


    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        return 0.0;
    }


}
