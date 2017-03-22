/**
 * Created by d_d on 2/17/17.
 */
public class RetrievalModelIndri extends RetrievalModel {
    protected double lambda, mu;


    public String defaultQrySopName() {
        return new String("#and");
    }

    public RetrievalModelIndri(double lambda, double mu) {
        this.lambda = lambda;
        this.mu = mu;
    }
}
