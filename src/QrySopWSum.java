import java.io.IOException;

/**
 * Created by d_d on 2/17/17.
 */
public class QrySopWSum extends QrySop {

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
        double w = 0;
        for (int i = 0; i < this.args.size(); i ++) {
            w += this.args.get(i).weight;
        }
        double score = 0;
        for (int i = 0; i < this.args.size(); i ++) {
            QrySop q_i = (QrySop) this.args.get(i);
            score += ((q_i.weight / w) * q_i.getDefaultScore(r, docid));
        }
        return score;
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WSUM operator.");
        }
    }

    /**
     * Get score for Indri method. Formula is defined in slide.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document's Indri score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {
        double w = 0;
        for (int i = 0; i < this.args.size(); i ++) {
            w += this.args.get(i).weight;
        }
        double score = 0.0;
        int docid = this.docIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i ++) {
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid) {
                score += ((q_i.weight / w) * q_i.getScore(r));
            } else {
                score += ((q_i.weight / w) * q_i.getDefaultScore(r, docid));
            }
        }
        return score;
    }

}
