package json;

import java.math.BigInteger;

public class ProofOfKnowledgeResendingFatherRoundReal {

    BigInteger c1, c2, z1, z2, a1, a2;
    int nodeIndex;

    public ProofOfKnowledgeResendingFatherRoundReal(BigInteger c1, BigInteger c2, BigInteger z1, BigInteger z2, BigInteger a1, BigInteger a2, int nodeIndex) {
        this.c1 = c1;
        this.c2 = c2;
        this.z1 = z1;
        this.z2 = z2;
        this.a1 = a1;
        this.a2 = a2;
        this.nodeIndex = nodeIndex;
    }

    public BigInteger getC1() {
        return c1;
    }

    public BigInteger getC2() {
        return c2;
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
