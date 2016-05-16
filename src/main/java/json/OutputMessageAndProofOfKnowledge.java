package json;

import participantnode.OutputMessage;

/**
 *
 */
public class OutputMessageAndProofOfKnowledge {

    private OutputMessage outputMessage;
    private ProofOfKnowledge proofOfKnowledge;

    /**
     *
     * @param outputMessage output message
     * @param proofOfKnowledge proof of knowledge
     */
    public OutputMessageAndProofOfKnowledge(OutputMessage outputMessage, ProofOfKnowledge proofOfKnowledge) {
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
    public ProofOfKnowledge getProofOfKnowledge() {
        return proofOfKnowledge;
    }
}
