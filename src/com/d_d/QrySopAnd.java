package com.d_d;

import java.io.IOException;

/**
 * Created by d_d on 2/1/17.
 */
public class QrySopAnd extends QrySop {
    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchAll(r);
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
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (this.docIteratorHasMatchCache()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < this.args.size(); i++) {
            double tf = ((QrySop) this.args.get(i)).getScore(r);
            min = min > tf ? tf : min;
        }
        return min;
    }
}
