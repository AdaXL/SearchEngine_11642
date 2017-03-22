import java.io.IOException;

/**
 * Created by d_d on 2/17/17.
 */
public class QrySopSum extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    /**
     *  Add getDefaultScore for Indri retrieval method, for those Qry that doesn't have a match.
     * @param r The retrieval model that determines how scores are calculated.
     * @param docid The docid that the Qry doesn't match.
     * @return The document's default score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        return 0.0;
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     * Add score calculator for BM25 method.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document BM25 score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreBM25(RetrievalModel r) throws IOException {
        double sum = 0;
        int docid = this.docIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i ++) {
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid) {
                sum += q_i.getScore(r);
            }
        }
        return sum;
    }
}
