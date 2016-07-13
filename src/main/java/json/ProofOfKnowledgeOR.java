package json;

import java.math.BigInteger;

public class ProofOfKnowledgeOR {

    BigInteger c1, c2, s1, s2, y1, y2;
    int nodeIndex;

    public ProofOfKnowledgeOR(BigInteger c1, BigInteger c2, BigInteger s1, BigInteger s2, BigInteger y1, BigInteger y2, int nodeIndex) {
        this.c1 = c1;
        this.c2 = c2;
        this.s1 = s1;
        this.s2 = s2;
        this.y1 = y1;
        this.y2 = y2;
        this.nodeIndex = nodeIndex;
    }

    public BigInteger getC1() {
        return c1;
    }

    public BigInteger getC2() {
        return c2;
    }

    public BigInteger getS1() {
        return s1;
    }

    public BigInteger getS2() {
        return s2;
    }

    public BigInteger getY1() {
        return y1;
    }

    public BigInteger getY2() {
        return y2;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }
}
