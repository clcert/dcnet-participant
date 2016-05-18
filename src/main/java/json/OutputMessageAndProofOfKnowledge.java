package json;

import participantnode.OutputMessage;

/**
 *
 */
public class OutputMessageAndProofOfKnowledge {

    private OutputMessage outputMessage;
    private ProofOfKnowledgePedersen proofOfKnowledge;

    /**
     *
     * @param outputMessage output message
     * @param proofOfKnowledge proof of knowledge
     */
    public OutputMessageAndProofOfKnowledge(OutputMessage outputMessage, ProofOfKnowledgePedersen proofOfKnowledge) {
        this.outputMessage = outputMessage;
        this.proofOfKnowledge = proofOfKnowledge;
    }

    /**
     *
     * @return output message
     */
    public OutputMessage getOutputMessage() {
        return outputMessage;
    }

    /**
     *
     * @return proof of knowledge
     */
    public ProofOfKnowledgePedersen getProofOfKnowledge() {
        return proofOfKnowledge;
    }
}
