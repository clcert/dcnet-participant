package json;

import java.math.BigInteger;

public class CommitmentAndProofOfKnowledge {

    private BigInteger commitment;
    private ProofOfKnowledge proofOfKnowledge;

    public CommitmentAndProofOfKnowledge(BigInteger commitment, ProofOfKnowledge proofOfKnowledge) {
        this.commitment = commitment;
        this.proofOfKnowledge = proofOfKnowledge;
    }

    public BigInteger getCommitment() {
        return commitment;
    }

    public ProofOfKnowledge getProofOfKnowledge() {
        return proofOfKnowledge;
    }

}
