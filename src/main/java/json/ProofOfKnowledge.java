package json;

import java.math.BigInteger;

/**
 *
 */
public class ProofOfKnowledge {

    private BigInteger g, z, a;
    private int nodeIndex;

    /**
     *
     * @param g generator g
     * @param z z = g^r
     * @param a a = r + b^w
     * @param nodeIndex index of participant node that sends the proof
     */
    public ProofOfKnowledge(BigInteger g, BigInteger z, BigInteger a, int nodeIndex) {
        this.g = g;
        this.z = z;
        this.a = a;
        this.nodeIndex = nodeIndex;
    }

    public BigInteger getG() {
        return g;
    }

    public BigInteger getZ() {
        return z;
    }

    public BigInteger getA() {
        return a;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

}
