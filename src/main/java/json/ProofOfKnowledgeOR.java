package json;

import java.math.BigInteger;

public class ProofOfKnowledgeOR {

    BigInteger c1, c2, z1, z2, z3, a1, a2, a3;
    int nodeIndex;

    public ProofOfKnowledgeOR(BigInteger c1, BigInteger c2, BigInteger z1, BigInteger z2, BigInteger z3, BigInteger a1, BigInteger a2, BigInteger a3, int nodeIndex) {
        this.c1 = c1;
        this.c2 = c2;
        this.z1 = z1;
        this.z2 = z2;
        this.z3 = z3;
        this.a1 = a1;
        this.a2 = a2;
        this.a3 = a3;
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

    public BigInteger getZ3() {
        return z3;
    }

    public BigInteger getA1() {
        return a1;
    }

    public BigInteger getA2() {
        return a2;
    }

    public BigInteger getA3() {
        return a3;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }
}
