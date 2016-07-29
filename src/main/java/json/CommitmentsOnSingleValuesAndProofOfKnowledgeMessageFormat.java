package json;

public class CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat {

    CommitmentsOnSingleValues commitmentsOnSingleValues;
    ProofOfKnowledgeOR proofOfKnowledgeOR;

    public CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat(CommitmentsOnSingleValues commitmentsOnSingleValues, ProofOfKnowledgeOR proofOfKnowledgeOR) {
        this.commitmentsOnSingleValues = commitmentsOnSingleValues;
        this.proofOfKnowledgeOR = proofOfKnowledgeOR;
    }

    public CommitmentsOnSingleValues getCommitmentsOnSingleValues() {
        return commitmentsOnSingleValues;
    }

    public ProofOfKnowledgeOR getProofOfKnowledgeOR() {
        return proofOfKnowledgeOR;
    }
}
