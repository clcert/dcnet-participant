package json;

import java.math.BigInteger;

/**
 *
 */
public class ProofOfKnowledgePedersen {

    private BigInteger d, u, v;
    private int nodeIndex;

    /**
     * @param d         commitment on random values (y, s)
     * @param u         u = y + ex
     * @param v         v = s + er
     * @param nodeIndex index of participant node that sends the proof
     */
    public ProofOfKnowledgePedersen(BigInteger d, BigInteger u, BigInteger v, int nodeIndex) {
        this.d = d;
        this.u = u;
        this.v = v;
        this.nodeIndex = nodeIndex;
    }

    /**
     * @return commitment d
     */
    public BigInteger getD() {
        return d;
    }

    /**
     * @return value u
     */
    public BigInteger getU() {
        return u;
    }

    /**
     * @return value v
     */
    public BigInteger getV() {
        return v;
    }

    /**
     * @return index of participant node sending this proof of knowledge
     */
    public int getNodeIndex() {
        return nodeIndex;
    }

}
