package json;

import java.math.BigInteger;

public class ProofOfKnowledge {

    private BigInteger c, d, e, u, v;

    public ProofOfKnowledge(BigInteger c, BigInteger d, BigInteger e, BigInteger u, BigInteger v) {
        this.c = c;
        this.d = d;
        this.e = e;
        this.u = u;
        this.v = v;
    }

    public BigInteger getC() {
        return c;
    }

    public BigInteger getD() {
        return d;
    }

    public BigInteger getE() {
        return e;
    }

    public BigInteger getU() {
        return u;
    }

    public BigInteger getV() {
        return v;
    }

}
