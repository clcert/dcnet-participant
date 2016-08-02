package json;

public class CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat {

    CommitmentsOnSingleValues commitmentsOnSingleValues;
    ProofOfKnowledgeMessageFormat proofOfKnowledgeMessageFormat;

    public CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat(CommitmentsOnSingleValues commitmentsOnSingleValues, ProofOfKnowledgeMessageFormat proofOfKnowledgeMessageFormat) {
        this.commitmentsOnSingleValues = commitmentsOnSingleValues;
        this.proofOfKnowledgeMessageFormat = proofOfKnowledgeMessageFormat;
    }

    public CommitmentsOnSingleValues getCommitmentsOnSingleValues() {
        return commitmentsOnSingleValues;
    }

    public ProofOfKnowledgeMessageFormat getProofOfKnowledgeMessageFormat() {
        return proofOfKnowledgeMessageFormat;
    }
}
