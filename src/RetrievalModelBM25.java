/**
 * Created by d_d on 2/17/17.
 */
public class RetrievalModelBM25 extends RetrievalModel {
    protected double k_1, k_3, b;

    public String defaultQrySopName() {
        return new String("#sum");
    }

    /**
     *  Constructor for parameter initializaiton.
     * @param k_1 k_1 value
     * @param k_3 k_3 value
     * @param b b value.
     */
    public RetrievalModelBM25(double k_1, double k_3, double b) {
        this.k_1 = k_1;
        this.k_3 = k_3;
        this.b = b;
    }
}
