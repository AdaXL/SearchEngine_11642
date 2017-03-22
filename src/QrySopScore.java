/**
 * Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

    /**
     *  Document-independent values that should be determined just once.
     *  Some retrieval models have these, some don't.
     */

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchFirst(r);
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
        } else if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SCORE operator.");
        }
    }


    /**
     *  getScore for the Unranked retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     *  getScore for the Ranked retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score, hence the tf value.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        QryIop qry = this.getArg(0);
        return qry.docIteratorGetMatchPosting().tf;
    }

    /**
     *  Add getDefaultScore for Indri retrieval method, for those Qry that doesn't have a match.
     * @param r The retrieval model that determines how scores are calculated.
     * @param docid The docid that the Qry doesn't match.
     * @return The document's default score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        RetrievalModelIndri indri_r = (RetrievalModelIndri) r;
        QryIop qry = this.getArg(0);
        double p_mle = qry.getCtf() / (double) Idx.getSumOfFieldLengths(qry.getField());
        double doclen = Idx.getFieldLength(qry.getField(), docid);
        double score = (1 - indri_r.lambda) * ((0 + indri_r.mu * p_mle) / (doclen + indri_r.mu)) + indri_r.lambda * p_mle;
        return score;
    }

    /**
     * Add score calculator for BM25 method.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document BM25 score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreBM25(RetrievalModel r) throws IOException {
        RetrievalModelBM25 bm25_r = (RetrievalModelBM25) r;
        QryIop qry = this.getArg(0);
        if (qry.docIteratorHasMatch(r)) {
            double df = qry.getDf();
            double tf = qry.docIteratorGetMatchPosting().tf;
            double doclen = Idx.getFieldLength(qry.getField(), qry.docIteratorGetMatch());
            double avg_doclen = Idx.getSumOfFieldLengths(qry.getField()) / (double) Idx.getDocCount(qry.getField());
            double RSJ_weight = Math.log((Idx.getNumDocs() - df + 0.5) / (df + 0.5));

            // Avoid N/2 thing.
            double idf = RSJ_weight > 0 ? RSJ_weight : 0.0;

            double tf_weight = tf / (tf + bm25_r.k_1 * ((1 - bm25_r.b) + bm25_r.b * doclen / avg_doclen));
            double user_weight = (bm25_r.k_3 + 1.0) * 1 / (bm25_r.k_3 + 1);
            double score = idf * tf_weight * user_weight;
            return score;
        } else {
            return 0;
        }
    }

    /**
     * Get score for Indri method. Formula is defined in slide.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document's Indri score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {
        RetrievalModelIndri indri_r = (RetrievalModelIndri) r;
        QryIop qry = this.getArg(0);
        if (qry.docIteratorHasMatch(r)) {
            double tf = qry.docIteratorGetMatchPosting().tf;
            double p_mle = qry.getCtf() / (double) Idx.getSumOfFieldLengths(qry.getField());
            double doclen = Idx.getFieldLength(qry.getField(), qry.docIteratorGetMatch());
            double score = (1.0 - indri_r.lambda) * ((tf + indri_r.mu * p_mle) / (doclen + indri_r.mu)) + indri_r.lambda * p_mle;
            return score;
        } else {
            return 0;
        }
    }


    /**
     *  Initialize the query operator (and its arguments), including any
     *  internal iterators.  If the query operator is of type QryIop, it
     *  is fully evaluated, and the results are stored in an internal
     *  inverted list that may be accessed via the internal iterator.
     *  @param r A retrieval model that guides initialization
     *  @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException {

        Qry q = this.args.get(0);
        q.initialize(r);
    }

}
