package json;

import java.math.BigInteger;

public class CommitmentsOnSingleValues {

    private BigInteger commitmentOnPlainMessage;
    private BigInteger commitmentOnRandomPadding;
    private BigInteger commitmentOnFinalBit;
    private int nodeIndex;

    public CommitmentsOnSingleValues(BigInteger a, BigInteger b, BigInteger c, int d) {
        this.commitmentOnPlainMessage = a;
        this.commitmentOnRandomPadding = b;
        this.commitmentOnFinalBit = c;
        this.nodeIndex = d;
    }

    public BigInteger getCommitmentOnPlainMessage() {
        return commitmentOnPlainMessage;
    }

    public BigInteger getCommitmentOnRandomPadding() {
        return commitmentOnRandomPadding;
    }

    public BigInteger getCommitmentOnFinalBit() {
        return commitmentOnFinalBit;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }
}
