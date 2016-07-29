package json;

import java.math.BigInteger;

/**
 *
 */
public class ProofOfKnowledgeAND {

    private BigInteger g, h, z1, z2, a1, a2;
    private int nodeIndex;

    /**
     * @param g
     * @param h
     * @param z1
     * @param z2
     * @param a1
     * @param a2
     * @param nodeIndex
     */
    public ProofOfKnowledgeAND(BigInteger g, BigInteger h, BigInteger z1, BigInteger z2, BigInteger a1, BigInteger a2, int nodeIndex) {
        this.g = g;
        this.h = h;
        this.z1 = z1;
        this.z2 = z2;
        this.a1 = a1;
        this.a2 = a2;
        this.nodeIndex = nodeIndex;
    }

    public BigInteger getG() {
        return g;
    }

    public BigInteger getH() {
        return h;
    }

    public BigInteger getZ1() {
        return z1;
    }

    public BigInteger getZ2() {
        return z2;
    }

    public BigInteger getA1() {
        return a1;
    }

    public BigInteger getA2() {
        return a2;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }
}