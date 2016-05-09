package json;

import java.math.BigInteger;

/**
 *
 */
public class ProofOfKnowledge {

    private BigInteger c, d, e, u, v;
    private int nodeIndex;

    /**
     *
     * @param c commitment value on (x, r)
     * @param d commitment on random values (y, s)
     * @param e random value calculated as hash(...), known as challenge
     * @param u u = y + ex
     * @param v v = s + er
     * @param nodeIndex index of participant node that sends the proof
     */
    public ProofOfKnowledge(BigInteger c, BigInteger d, BigInteger e, BigInteger u, BigInteger v, int nodeIndex) {
        this.c = c;
        this.d = d;
        this.e = e;
        this.u = u;
        this.v = v;
        this.nodeIndex = nodeIndex;
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

    public int getNodeIndex() {
        return nodeIndex;
    }
}
