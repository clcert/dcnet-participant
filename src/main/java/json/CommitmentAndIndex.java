package json;

import java.math.BigInteger;

public class CommitmentAndIndex {

    private BigInteger commitment;
    private int nodeIndex;

    public CommitmentAndIndex(BigInteger commitment, int nodeIndex) {
        this.commitment = commitment;
        this.nodeIndex = nodeIndex;
    }

    public BigInteger getCommitment() {
        return commitment;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

}
