package json;

import java.math.BigInteger;

/**
 *
 */
public class CommitmentAndProofOfKnowledge {

    private BigInteger commitment;
    private ProofOfKnowledgePedersen proofOfKnowledge;

    /**
     *
     * @param commitment commitment
     * @param proofOfKnowledge proof of knowledge on values hidden in commitment
     */
    public CommitmentAndProofOfKnowledge(BigInteger commitment, ProofOfKnowledgePedersen proofOfKnowledge) {
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
    public ProofOfKnowledgePedersen getProofOfKnowledge() {
        return proofOfKnowledge;
    }

}
