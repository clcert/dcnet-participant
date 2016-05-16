package json;

import participantnode.OutputMessage;

public class OutputMessageAndProofOfKnowledge {

    OutputMessage outputMessage;
    ProofOfKnowledge proofOfKnowledge;

    public OutputMessageAndProofOfKnowledge(OutputMessage outputMessage, ProofOfKnowledge proofOfKnowledge) {
        this.outputMessage = outputMessage;
        this.proofOfKnowledge = proofOfKnowledge;
    }

    public OutputMessage getOutputMessage() {
        return outputMessage;
    }

    public ProofOfKnowledge getProofOfKnowledge() {
        return proofOfKnowledge;
    }
}
