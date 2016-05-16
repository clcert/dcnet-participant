package json;

import java.math.BigInteger;

/**
 *
 */
public class CommitmentAndProofOfKnowledge {

    private BigInteger commitment;
    private ProofOfKnowledge proofOfKnowledge;

    /**
     *
     * @param commitment commitment
     * @param proofOfKnowledge proof of knowledge on values hidden in commitment
     */
    public CommitmentAndProofOfKnowledge(BigInteger commitment, ProofOfKnowledge proofOfKnowledge) {
        this.commitment = commitment;
        this.proofOfKnowledge = proofOfKnowledge;
    }

    /**
     *
     * @return commitment
     */
    public BigInteger getCommitment() {
        return commitment;
    }

    /**
     *
     * @return proof of knowledge
     */
    public ProofOfKnowledge getProofOfKnowledge() {
        return proofOfKnowledge;
    }

}
