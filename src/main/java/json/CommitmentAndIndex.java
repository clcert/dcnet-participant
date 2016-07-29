package json;

import java.math.BigInteger;

/**
 *
 */
public class CommitmentAndIndex {

    private BigInteger commitment;
    private int nodeIndex;

    /**
     * @param commitment commitment
     * @param nodeIndex  index of current participant node
     */
    public CommitmentAndIndex(BigInteger commitment, int nodeIndex) {
        this.commitment = commitment;
        this.nodeIndex = nodeIndex;
    }

    /**
     * @return commitment
     */
    public BigInteger getCommitment() {
        return commitment;
    }

    /**
     * @return index of current participant node
     */
    public int getNodeIndex() {
        return nodeIndex;
    }

}
