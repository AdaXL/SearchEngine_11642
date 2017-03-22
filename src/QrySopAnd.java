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
        if (r instanceof RetrievalModelIndri) {

            // Indri uses docIteratorHasMatchMin to handle those that doesn't have match but has weight assigned.

            return this.docIteratorHasMatchMin(r);
        }
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
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  Add getDefaultScore for Indri retrieval method, for those Qry that doesn't have a match.
     * @param r The retrieval model that determines how scores are calculated.
     * @param docid The docid that the Qry doesn't match.
     * @return The document's default score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        double score = 1.0;
        for (int i = 0; i < this.args.size(); i ++) {
            QrySop q_i = (QrySop) this.args.get(i);
            score *= q_i.getDefaultScore(r, docid);
        }
        return Math.pow(score, 1.0 / this.args.size());
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


    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        // Finding the min score of #AND operator's arguments.
        double min = Double.MAX_VALUE;
        for (int i = 0; i < this.args.size(); i++) {
            double tf = ((QrySop) this.args.get(i)).getScore(r);
            min = min > tf ? tf : min;
        }
        return min;
    }

    /**
     * Get score for Indri method. Formula is defined in slide.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document's Indri score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri(RetrievalModel r) throws IOException {
        double score = 1.0;
        int docid = this.docIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i ++) {
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid) {
                score *= q_i.getScore(r);
            } else {
                score *= q_i.getDefaultScore(r, docid);
            }
        }
        return Math.pow(score, 1.0 / this.args.size());
    }
}
