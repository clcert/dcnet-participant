package participantnode;

import com.google.gson.Gson;
import crypto.Commitment;
import crypto.PedersenCommitment;
import crypto.ZeroKnowledgeProof;
import dcnet.DCNETProtocol;
import dcnet.Room;
import json.*;
import keygeneration.DiffieHellman;
import keygeneration.KeyGeneration;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedList;

/**
 *
 */
public class SessionManager {

    private ZMQ.Socket[] repliers,
            requestors;
    private boolean messageTransmitted,
            finished,
            messageInThisRound;
    private int round,
            realRoundsPlayed,
            nextRoundAllowedToSend,
            collisionSize,
            messagesSentWithNoCollisions;
    private Dictionary<Integer, BigInteger> messagesSentInPreviousRounds;
    private LinkedList<Integer> nextRoundsToHappen;
    private PedersenCommitment pedersenCommitment;
    private long executionTime, firstMessageTime, averageTimePerMessage, totalSyncTime;

    /**
     * Initialize all parameters of SessionManager with default values
     */
    public SessionManager() {
        messageTransmitted = false;
        messageInThisRound = true;
        round = 1;
        realRoundsPlayed = 0;
        nextRoundAllowedToSend = 1;
        collisionSize = 0;
        messagesSentInPreviousRounds = new Hashtable<>();
        messagesSentWithNoCollisions = 0;
        finished = false;
        nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);
        executionTime = 0;
        firstMessageTime = 0;
        pedersenCommitment = new PedersenCommitment();
        totalSyncTime = 0;
    }

    /**
     * Method that runs a single session for a single participant node within an specific room
     *
     * @param nodeIndex                index of the participant node
     * @param participantMessage       string with the message that participant node wants to communicate
     * @param room                     room where the message is going to be send
     * @param node                     participant node
     * @param receiverThread           thread where participant node is listening
     * @param messagesList             test
     * @param cheaterNode              test
     * @param observableMessageArrived test
     * @throws IOException              test
     * @throws NoSuchAlgorithmException test
     */
    public void runSession(int nodeIndex, String participantMessage, boolean cheaterNode, Room room, ParticipantNode node, ZMQ.Socket receiverThread, ArrayList<String> messagesList, DCNETProtocol.ObservableMessageArrived observableMessageArrived) throws IOException, NoSuchAlgorithmException {
        boolean emptyMessage = false;

        if (participantMessage.equals("")) {
            participantMessage = "0";
            emptyMessage = true;
        }

        // Create an outputMessage
        OutputMessage outputParticipantMessage = new OutputMessage();
        outputParticipantMessage.setPaddingLength(room.getPadLength());
        outputParticipantMessage.setParticipantMessage(participantMessage, room);
        BigInteger plainMessageWithRandomPadding = outputParticipantMessage.getPlainMessageWithRandomPadding();

        // Create a zeroMessage
        OutputMessage zeroMessage = new OutputMessage();
        zeroMessage.setParticipantMessage("0", room);
        zeroMessage.setPaddingLength(room.getPadLength());

        // Synchronize nodes at the beginning to solve slow joiner problem
        synchronizeNodes(nodeIndex, repliers, requestors, room);

        // Set values of subsequently commitments with the public info of the Room
        pedersenCommitment = new PedersenCommitment(room.getG(), room.getH(), room.getQ(), room.getP());

        // Initialize ZeroKnowledgeProof with values of the room
        ZeroKnowledgeProof zkp = new ZeroKnowledgeProof(nodeIndex);

        // Store commitments on plain message of current participant node
        Dictionary<Integer, BigInteger> commitmentsOnPlainMessage = new Hashtable<>();
        // Store random values for commitments on plain message of current participant node
        Dictionary<Integer, BigInteger> randomsForPlainMessage = new Hashtable<>();

        // Store commitments on plain messages of others participant nodes in the room
        @SuppressWarnings("unchecked")
        Hashtable<Integer, BigInteger>[] receivedCommitmentsOnPlainMessages = new Hashtable[room.getRoomSize()];
        for (int i = 0; i < receivedCommitmentsOnPlainMessages.length; i++)
            receivedCommitmentsOnPlainMessages[i] = new Hashtable<>();

        // Set time to measure entire protocol
        long t1 = System.nanoTime();

        /* ROUNDS */
        // Each loop of this while is a different round
        while (!Thread.currentThread().isInterrupted()) {

            // Check if the protocol was finished in the last round played.
            // If it so, let know to the receiver thread, wait for his response and break the loop
            if (finished) {
                receiverThread.send("FINISHED");
                receiverThread.recvStr();
                break;
            }
            // If it is not finished yet, obtain which round we need to play and send this round to the receiver thread
            else {
                round = nextRoundsToHappen.removeFirst();
                receiverThread.send("" + round);
            }

            // Variables to store the resulting message of the round
            BigInteger sumOfM, sumOfT, sumOfO = BigInteger.ZERO;

            // Store commitments on keys and on message for future checking
            BigInteger[] commitmentsOnKey = new BigInteger[room.getRoomSize()];
            BigInteger[] commitmentsOnMessage = new BigInteger[room.getRoomSize()];

            /* REAL ROUND (first and even rounds) **/
            if (round == 1 || round % 2 == 0) {

                // Check if in this round the participant will send a real message or a zero message
                messageInThisRound = !messageTransmitted && nextRoundAllowedToSend == round;

                // Set variable that we are playing a real round and add one to the count
                realRoundsPlayed++;

                /* KEY SHARING PART **/
                // Initialize KeyGeneration
                /*KeyGeneration keyGeneration = new SecretSharing(room.getRoomSize(), nodeIndex, repliers, requestors, room);*/
                KeyGeneration keyGeneration = new DiffieHellman(room.getRoomSize() - 1, room.getG(), room.getP(), nodeIndex, repliers, requestors, room);

                // Generate Participant Node values
                keyGeneration.generateParticipantNodeValues();

                // Get other participants values (to produce cancellation keys)
                keyGeneration.getOtherParticipantNodesValues();

                // Generation of the main key round value (operation over the shared key values)
                BigInteger keyRoundValue = keyGeneration.getParticipantNodeRoundKeyValue();

                /* SEND COMMITMENT AND POK ON KEY **/
                // Get round keys (shared keys) of the current participant node
                BigInteger[] roundKeys = keyGeneration.getRoundKeys();

                // Get shared random values
                BigInteger[] sharedRandomValues = keyGeneration.getSharedRandomValues();

                // Calculate and save commitments on each round key
                BigInteger[] commitmentsOnKeys = new BigInteger[roundKeys.length];
                for (int i = 0; i < roundKeys.length; i++)
                    commitmentsOnKeys[i] = pedersenCommitment.calculateCommitment(roundKeys[i], sharedRandomValues[i]);

                // Retrieve random for commitment on key
                BigInteger randomRoundValue = calculateRandomForCommitmentOnKey(sharedRandomValues);

                // Generate general commitment value for the resulting round key (operation over round keys)
                BigInteger commitmentOnKey = generateCommitmentOnKey(commitmentsOnKeys, room);

                // Generate proof of knowledge on key stored in commitment
                ProofOfKnowledgePedersen proofOfKnowledgeOnKey = zkp.generateProofOfKnowledgePedersen(commitmentOnKey, room.getG(), keyRoundValue, room.getH(), randomRoundValue, room.getQ(), room.getP());

                // Generate Json string containing commitmentOnKey and proofOfKnowledge
                CommitmentAndProofOfKnowledge commitmentAndProofOfKnowledgeOnKey = new CommitmentAndProofOfKnowledge(commitmentOnKey, proofOfKnowledgeOnKey);
                String commitmentAndProofOfKnowledgeOnKeyJson = new Gson().toJson(commitmentAndProofOfKnowledgeOnKey, CommitmentAndProofOfKnowledge.class);

                // Send commitment on key and index to the room
                node.getSender().send(commitmentAndProofOfKnowledgeOnKeyJson);

                /* RECEIVE COMMITMENTS AND POKs ON KEYS **/
                // Receive commitments of other participant nodes where it needs to check that the multiplication of all is 1
                BigInteger multiplicationOnCommitments = BigInteger.ONE;
                for (int i = 0; i < room.getRoomSize(); i++) {
                    // Wait response from Receiver thread as a string
                    String receivedCommitmentAndProofOfKnowledgeOnKeyJson = receiverThread.recvStr();

                    // Transform string (json) to CommitmentAndProofOfKnowledge object
                    CommitmentAndProofOfKnowledge receivedCommitmentAndProofOfKnowledgeOnKey = new Gson().fromJson(receivedCommitmentAndProofOfKnowledgeOnKeyJson, CommitmentAndProofOfKnowledge.class);

                    // Get commitmentOnKey
                    BigInteger receivedCommitmentOnKey = receivedCommitmentAndProofOfKnowledgeOnKey.getCommitment();

                    // Store commitment for future checking
                    commitmentsOnKey[receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge().getNodeIndex() - 1] = receivedCommitmentOnKey;

                    // Verify proofOfKnowledge
                    if (!zkp.verifyProofOfKnowledgePedersen(receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge(), receivedCommitmentOnKey, room.getG(), room.getH(), room.getQ(), room.getP()))
                        System.err.println("WRONG PoK on Key. Round: " + round + ", Node: " + receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge().getNodeIndex());

                    // Calculate multiplication of incoming commitments
                    multiplicationOnCommitments = multiplicationOnCommitments.multiply(receivedCommitmentOnKey).mod(room.getP());
                }
                // Check that multiplication result is 1
                if (!multiplicationOnCommitments.equals(BigInteger.ONE))
                    System.err.println("Round " + round + " commitments on keys are WRONG");

                /* SET MESSAGES AND OBJECTS OF THIS ROUND **/
                // Set protocol message to make a commitment to and add round key to the message to construct Json that will be sent
                BigInteger protocolRoundMessage;
                BigInteger plainMessage, randomPadding, finalBit;
                if (messageInThisRound) {
                    protocolRoundMessage = outputParticipantMessage.getProtocolMessage();
                    outputParticipantMessage.setRoundKeyValue(keyRoundValue);
                    plainMessage = outputParticipantMessage.getPlainMessage();
                    randomPadding = outputParticipantMessage.getRandomPadding();
                    finalBit = outputParticipantMessage.getFinalBit();
                } else {
                    protocolRoundMessage = BigInteger.ZERO;
                    zeroMessage.setRoundKeyValue(keyRoundValue);
                    plainMessage = zeroMessage.getPlainMessage();
                    randomPadding = zeroMessage.getRandomPadding();
                    finalBit = zeroMessage.getFinalBit();
                }

                /* SEND CORRECT FORMAT OF MESSAGE PROOF **/
                // Random values
                BigInteger randomForPlainMessage = pedersenCommitment.generateRandom();
                BigInteger randomForRandomPadding = pedersenCommitment.generateRandom();
                BigInteger randomForFinalBit = pedersenCommitment.generateRandom();

                //randomsForPlainMessage.add(round, randomForPlainMessage);
                randomsForPlainMessage.put(round, randomForPlainMessage);

                // Commitments for single values
                BigInteger commitmentOnPlainMessage = pedersenCommitment.calculateCommitment(plainMessage, randomForPlainMessage);
                BigInteger commitmentOnRandomPadding = pedersenCommitment.calculateCommitment(randomPadding, randomForRandomPadding);
                BigInteger commitmentOnFinalBit = pedersenCommitment.calculateCommitment(finalBit, randomForFinalBit);

                // commitmentsOnPlainMessage.add(round, commitmentOnPlainMessage);
                commitmentsOnPlainMessage.put(round, commitmentOnPlainMessage);

                // Create Object with single commitments
                CommitmentsOnSingleValues commitmentsOnSingleValues = new CommitmentsOnSingleValues(commitmentOnPlainMessage, commitmentOnRandomPadding, commitmentOnFinalBit, nodeIndex);

                ProofOfKnowledgeMessageFormat proofForMessageFormat;
                BigInteger _comm = room.getG().modInverse(room.getP()).multiply(commitmentOnFinalBit).mod(room.getP()); // _comm = C_b * g^{-1}
                if (messageInThisRound && !emptyMessage) {
                    proofForMessageFormat = zkp.generateProofOfKnowledgeMessageFormatX1(_comm, room.getH(), randomForFinalBit, commitmentOnFinalBit, commitmentOnPlainMessage, room.getQ(), room.getP());
                } else {
                    proofForMessageFormat = zkp.generateProofOfKnowledgeMessageFormatX2X3(_comm, room.getH(), commitmentOnFinalBit, randomForFinalBit, commitmentOnPlainMessage, randomForPlainMessage, room.getQ(), room.getP());
                }

                CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat = new CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat(commitmentsOnSingleValues, proofForMessageFormat);
                String commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormatJson = new Gson().toJson(commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat, CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat.class);
                node.getSender().send(commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormatJson);

                /* RECEIVE COMMITMENTS ON SINGLE VALUES AND POK ON CORRECT MESSAGE FORMAT **/
                for (int i = 0; i < room.getRoomSize(); i++) {
                    String receivedCommitmentsOnSingleValuesAndPOKMessageFormatJson = receiverThread.recvStr();
                    CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat receivedCommitmentsOnSingleValuesAndPOKMessageFormat = new Gson().fromJson(receivedCommitmentsOnSingleValuesAndPOKMessageFormatJson, CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat.class);

                    BigInteger receivedCommitmentOnPlainMessage = receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getCommitmentsOnSingleValues().getCommitmentOnPlainMessage();
                    BigInteger receivedCommitmentOnRandomPadding = receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getCommitmentsOnSingleValues().getCommitmentOnRandomPadding();
                    BigInteger receivedCommitmentOnFinalBit = receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getCommitmentsOnSingleValues().getCommitmentOnFinalBit();

                    int participantNodeIndex = receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getCommitmentsOnSingleValues().getNodeIndex();
                    receivedCommitmentsOnPlainMessages[participantNodeIndex - 1].put(round, receivedCommitmentOnPlainMessage);

                    BigInteger receivedCommitmentOnMessage = constructCommitmentOnMessage(receivedCommitmentOnPlainMessage, receivedCommitmentOnRandomPadding, receivedCommitmentOnFinalBit, room);
                    commitmentsOnMessage[participantNodeIndex - 1] = receivedCommitmentOnMessage;

                    ProofOfKnowledgeMessageFormat receivedProofForMessageFormat = receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getProofOfKnowledgeMessageFormat();

                    // Verify Proof of Knowledge
                    BigInteger _rcvComm = room.getG().modInverse(room.getP()).multiply(receivedCommitmentOnFinalBit).mod(room.getP()); // _comm = C_b * g^{-1}
                    if (!zkp.verifyProofOfKnowledgeMessageFormat(receivedProofForMessageFormat, _rcvComm, receivedCommitmentOnFinalBit, receivedCommitmentOnPlainMessage, room.getH(), room.getQ(), room.getP()))
                        System.err.println("WRONG PoK on Message Format. Round: " + round + ", Node: " + receivedProofForMessageFormat.getNodeIndex());
                }

                /* SEND POK ON MESSAGE **/
                // Generate Commitment on Message
                BigInteger commitmentOnMessage = constructCommitmentOnMessage(commitmentOnPlainMessage, commitmentOnRandomPadding, commitmentOnFinalBit, room);

                // Generate random value for commitment
                BigInteger randomForCommitmentOnMessage = calculateRandomForCommitmentOnMessage(randomForPlainMessage, randomForRandomPadding, randomForFinalBit, room);

                // Generate ProofOfKnowledgePedersen associated with the commitment for the protocol message, using randomForCommitment as the necessary random value
                ProofOfKnowledgePedersen proofOfKnowledgeOnMessage = zkp.generateProofOfKnowledgePedersen(commitmentOnMessage, room.getG(), protocolRoundMessage, room.getH(), randomForCommitmentOnMessage, room.getQ(), room.getP());
                String proofOfKnowledgeOnMessageJson = new Gson().toJson(proofOfKnowledgeOnMessage, ProofOfKnowledgePedersen.class);

                // Send Json to the room (which contains the proofOfKnowledge)
                node.getSender().send(proofOfKnowledgeOnMessageJson);

                /* RECEIVE COMMITMENTS AND POKs ON MESSAGES **/
                // Receive proofs of other participant nodes where we need to check each of them
                for (int i = 0; i < room.getRoomSize(); i++) {
                    // Wait response from Receiver thread as a String (json)
                    String receivedProofOfKnowledgeOnMessageJson = receiverThread.recvStr();
                    // Transform String (json) to object ProofOfKnowledgePedersen
                    ProofOfKnowledgePedersen receivedProofOfKnowledgeOnMessage = new Gson().fromJson(receivedProofOfKnowledgeOnMessageJson, ProofOfKnowledgePedersen.class);
                    // Verify proof of knowledge
                    if (!zkp.verifyProofOfKnowledgePedersen(receivedProofOfKnowledgeOnMessage, commitmentsOnMessage[receivedProofOfKnowledgeOnMessage.getNodeIndex() - 1], room.getG(), room.getH(), room.getQ(), room.getP()))
                        System.err.println("WRONG PoK on Message. Round: " + round + ", Node: " + receivedProofOfKnowledgeOnMessage.getNodeIndex());
                }

                /* SEND OUTPUT MESSAGE AND POK ASSOCIATED **/
                // Set Proof of Knowledge that is needed for round 1
                if (round == 1) {
                    // Calculate random for commitment as the sum of both random used before (commitment on key and commitment on message)
                    BigInteger randomForCommitmentOnOutputMessage = randomRoundValue.add(randomForCommitmentOnMessage);
                    // Commitment for the sum of both randomness used
                    BigInteger commitmentOnSumOfRandomness = new Commitment(room.getH(), room.getQ(), room.getP()).calculateCommitment(randomForCommitmentOnOutputMessage);
                    // Generate proofOfKnowledge for OutputMessage, as a commitment for the sum of both randomness used (for commitments on key and message)
                    ProofOfKnowledge proofOfKnowledgeOnOutputMessage = zkp.generateProofOfKnowledge(commitmentOnSumOfRandomness, room.getH(), randomForCommitmentOnOutputMessage, room.getQ(), room.getP());
                    // Generate Json string with Object containing both outputMessage and proofOfKnowledge
                    OutputMessageAndProofOfKnowledge outputMessageAndProofOfKnowledge = new OutputMessageAndProofOfKnowledge(outputParticipantMessage, proofOfKnowledgeOnOutputMessage);
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(outputMessageAndProofOfKnowledge, OutputMessageAndProofOfKnowledge.class);
                    // Send the Json to the room (which contains the outputMessage and the proofOfKnowledge)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                } else if ((round / 2) % 2 == 0 || round == 2) {
                    BigInteger divisionOfCommitments = commitmentOnPlainMessage.modInverse(room.getP()).multiply(commitmentsOnPlainMessage.get((round / 2)));
                    ProofOfKnowledgeResendingFatherRoundReal proofOfKnowledgeResendingFatherRoundReal;
                    OutputMessageAndProofOfKnowledgeResendingFatherRoundReal outputMessageAndProofOfKnowledgeResendingFatherRoundReal;
                    if (messageInThisRound) {
                        BigInteger subtractionOfRandomness = randomsForPlainMessage.get((round / 2)).subtract(randomForPlainMessage).mod(room.getQ());
                        proofOfKnowledgeResendingFatherRoundReal = zkp.generateProofOfKnowledgeResendingFatherRoundRealX2(commitmentOnPlainMessage, divisionOfCommitments, room.getH(), subtractionOfRandomness, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundReal = new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(outputParticipantMessage, proofOfKnowledgeResendingFatherRoundReal);
                    } else {
                        proofOfKnowledgeResendingFatherRoundReal = zkp.generateProofOfKnowledgeResendingFatherRoundRealX1(commitmentOnPlainMessage, room.getH(), randomForPlainMessage, divisionOfCommitments, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundReal = new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(zeroMessage, proofOfKnowledgeResendingFatherRoundReal);
                    }
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(outputMessageAndProofOfKnowledgeResendingFatherRoundReal, OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);
                    // Send the Json to the room (which contains the outputMessage and the proofOfKnowledge when the father round is real)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                } else {
                    // TODO: message not sent in real rounds between this and that previous one
                    int virtualFatherRound = (round / 2);
                    int nearestRealRound = getNearestRealRound(virtualFatherRound);
                    BigInteger divisionOfCommitments = commitmentOnPlainMessage.modInverse(room.getP()).multiply(commitmentsOnPlainMessage.get(nearestRealRound));
                    ProofOfKnowledgeResendingFatherRoundReal proofOfKnowledgeResendingFatherRoundVirtual;
                    OutputMessageAndProofOfKnowledgeResendingFatherRoundReal outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual;
                    if (messageInThisRound) {
                        BigInteger subtractionOfRandomness = randomsForPlainMessage.get(nearestRealRound).subtract(randomForPlainMessage).mod(room.getQ());
                        proofOfKnowledgeResendingFatherRoundVirtual = zkp.generateProofOfKnowledgeResendingFatherRoundRealX2(commitmentOnPlainMessage, divisionOfCommitments, room.getH(), subtractionOfRandomness, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual = new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(outputParticipantMessage, proofOfKnowledgeResendingFatherRoundVirtual);
                    } else {
                        proofOfKnowledgeResendingFatherRoundVirtual = zkp.generateProofOfKnowledgeResendingFatherRoundRealX1(commitmentOnPlainMessage, room.getH(), randomForPlainMessage, divisionOfCommitments, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual = new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(zeroMessage, proofOfKnowledgeResendingFatherRoundVirtual);
                    }
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual, OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);
                    // Send the Json to the room (which contains the outputMessage and the proofOfKnowledge when the father round is real)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                }

                // Subtract round key to the message in order to send a clear one in the next round
                if (messageInThisRound)
                    outputParticipantMessage.setRoundKeyValue(keyRoundValue.negate());
                else
                    zeroMessage.setRoundKeyValue(keyRoundValue.negate());

                /* RECEIVE OUTPUT MESSAGES AND POKs ASSOCIATED **/
                if (round == 1) {
                    // Variable to count how many messages were received from the receiver thread
                    int messagesReceivedInThisRound = 0;
                    // When this number equals the total number of participants nodes in the room, it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {
                        // Receive a message (json)
                        String messageReceivedFromReceiverThread = receiverThread.recvStr();
                        // Transform incoming message (json) to a OutputMessageAndProofOfKnowledge object
                        OutputMessageAndProofOfKnowledge outputMessageAndProofOfKnowledge = new Gson().fromJson(messageReceivedFromReceiverThread, OutputMessageAndProofOfKnowledge.class);
                        // Get index of participant node that is sending his proofOfKnowledge
                        int participantNodeIndex = outputMessageAndProofOfKnowledge.getProofOfKnowledge().getNodeIndex();
                        // Construct commitment on outputMessage as the multiplication of commitmentOnKey and commitmentOnMessage
                        BigInteger commitmentOnOutputMessage = commitmentsOnKey[participantNodeIndex - 1].multiply(commitmentsOnMessage[participantNodeIndex - 1]).mod(room.getP());
                        // Construct beta in order to verify proof of knowledge sent by the participant node
                        BigInteger beta = commitmentOnOutputMessage.multiply(room.getG().modPow(outputMessageAndProofOfKnowledge.getOutputMessage().getProtocolMessage(), room.getP()).modInverse(room.getP())).mod(room.getP());
                        // Verify the proofOfKnowledge with the values rescued before and do something if it's not valid
                        if (!zkp.verifyProofOfKnowledge(outputMessageAndProofOfKnowledge.getProofOfKnowledge(), beta, room.getH(), room.getQ(), room.getP()))
                            System.err.println("WRONG PoK on OutputMessage. Round: " + round + ", Node: " + participantNodeIndex);
                        // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledge.getOutputMessage().getProtocolMessage()).mod(room.getP());
                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                } else if ((round / 2) % 2 == 0 || round == 2) {
                    // Variable to count how many messages were received from the receiver thread
                    int messagesReceivedInThisRound = 0;
                    // When this number equals the total number of participants nodes in the room, it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {
                        // Receive a message
                        String messageReceivedFromReceiverThread = receiverThread.recvStr();

                        OutputMessageAndProofOfKnowledgeResendingFatherRoundReal outputMessageAndProofOfKnowledgeResendingFatherRoundReal = new Gson().fromJson(messageReceivedFromReceiverThread, OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);
                        int participantNodeIndex = outputMessageAndProofOfKnowledgeResendingFatherRoundReal.getProofOfKnowledgeResendingFatherRoundReal().getNodeIndex();

                        BigInteger commitmentOnPlainMessageNodeRound2K = receivedCommitmentsOnPlainMessages[participantNodeIndex - 1].get(round);
                        BigInteger commitmentOnPlainMessageNodeRoundK = receivedCommitmentsOnPlainMessages[participantNodeIndex - 1].get((round / 2));
                        BigInteger resultantCommitment = commitmentOnPlainMessageNodeRound2K.modInverse(room.getP()).multiply(commitmentOnPlainMessageNodeRoundK);

                        if (!zkp.verifyProofOfKnowledgeResendingFatherRoundReal(outputMessageAndProofOfKnowledgeResendingFatherRoundReal.getProofOfKnowledgeResendingFatherRoundReal(), commitmentOnPlainMessageNodeRound2K, resultantCommitment, room.getH(), room.getQ(), room.getP()))
                            System.err.println("WRONG PoK on Resending when father round is real. Round: " + round + ", Node: " + participantNodeIndex);

                        // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledgeResendingFatherRoundReal.getOutputMessage().getProtocolMessage()).mod(room.getP());
                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                } else {
                    // Variable to count how many messages were received from the receiver thread
                    int messagesReceivedInThisRound = 0;
                    // When this number equals the total number of participants nodes in the room, it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {
                        // Receive a message
                        String messageReceivedFromReceiverThread = receiverThread.recvStr();

                        OutputMessageAndProofOfKnowledgeResendingFatherRoundReal outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual = new Gson().fromJson(messageReceivedFromReceiverThread, OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);
                        int participantNodeIndex = outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual.getProofOfKnowledgeResendingFatherRoundReal().getNodeIndex();

                        BigInteger commitmentOnPlainMessageNodeRound2K = receivedCommitmentsOnPlainMessages[participantNodeIndex - 1].get(round);
                        int nearestRealRound = getNearestRealRound(round / 2);
                        BigInteger commitmentOnPlainMessageNodeNearestRealRound = receivedCommitmentsOnPlainMessages[participantNodeIndex - 1].get(nearestRealRound);
                        BigInteger resultantCommitment = commitmentOnPlainMessageNodeRound2K.modInverse(room.getP()).multiply(commitmentOnPlainMessageNodeNearestRealRound);

                        if (!zkp.verifyProofOfKnowledgeResendingFatherRoundReal(outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual.getProofOfKnowledgeResendingFatherRoundReal(), commitmentOnPlainMessageNodeRound2K, resultantCommitment, room.getH(), room.getQ(), room.getP()))
                            System.err.println("WRONG PoK on Resending when father round is virtual. Round: " + round + ", Node: " + participantNodeIndex);

                        // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual.getOutputMessage().getProtocolMessage()).mod(room.getP());
                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                }
            }

            /* VIRTUAL ROUND (odd rounds) **/
            else {
                // Recover messages sent in rounds (2*round) and round in order to construct the resulting message of this round (see Reference for more information)
                BigInteger sumOfOSentInRound2K = messagesSentInPreviousRounds.get(round - 1);
                BigInteger sumOfOSentInRoundK = messagesSentInPreviousRounds.get((round - 1) / 2);
                // Construct the resulting message of this round
                sumOfO = sumOfOSentInRoundK.subtract(sumOfOSentInRound2K);
            }

            /* EXTRACT ROUND MESSAGES VALUES **/
            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, sumOfO);
            // Divide sumOfO in sumOfM and sumOfT (see Reference for more information)
            sumOfM = sumOfO.divide(BigInteger.valueOf(room.getRoomSize() + 1));
            sumOfT = sumOfO.subtract(sumOfM.multiply(BigInteger.valueOf(room.getRoomSize() + 1)));

            // Print info about the messages sent in probabilistic mode
            if (!room.getNonProbabilisticMode()) {
                if (sumOfM.toString().length() > 15)
                    System.err.println("C_" + round + ":\t(" + sumOfM.toString().substring(0, 10) + "..., " + sumOfT + ")");
                else
                    System.err.println("C_" + round + ":\t(" + sumOfM + ", " + sumOfT + ")");
            }

            // If we are playing the first round, assign the size of the collision
            if (round == 1) {
                collisionSize = Integer.parseInt(sumOfT.toString());
                // If the size is 0, it means that no messages were sent during this session, so we finish the protocol
                if (collisionSize == 0) {
                    System.err.println("NO MESSAGES WERE SENT");
                    finished = true;
                    continue;
                }
            }

            /* NO COLLISION ROUND **/
            // <sumOfT> = 1 => No Collision Round => a message went through clearly, received by the rest of the nodes
            if (sumOfT.equals(BigInteger.ONE)) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                if (messagesSentWithNoCollisions == 1)
                    firstMessageTime = System.nanoTime() - t1;

                // Print message that went through the protocol
                String singleMessage = OutputMessage.getMessageWithoutRandomPadding(sumOfM, room);
                // Add message to List
                messagesList.add(singleMessage);
                // Set message to Observable object
                observableMessageArrived.setValue(singleMessage);

                /* If the message that went through is mine, my message was transmitted.
                 * We have to set the variable in order to start sending zero messages in subsequently rounds */
                if (plainMessageWithRandomPadding.equals(sumOfM))
                    messageTransmitted = true;

                /* If the number of messages that went through equals the collision size, the first collision was completely resolved.
                 * Set variable to finalize the protocol in the next round */
                if (messagesSentWithNoCollisions == collisionSize)
                    finished = true;
            } else {
                /* PROBLEMATIC ROUND **/
                // <sumOfT> == 0 => if we are in a deterministic mode, this means that someone cheated, and it's necessary to change the mode
                if (sumOfT.equals(BigInteger.ZERO)) {
                    // Change resending mode
                    if (room.getNonProbabilisticMode()) {
                        room.setNonProbabilisticMode(false);
                    }
                }

                /* COLLISION ROUND **/
                // <sumOfT> > 1 => A Collision was produced
                if (sumOfT.compareTo(BigInteger.ONE) > 0) {

                    /* PROBLEMATIC ROUND **/
                    // <sumOfT> repeats in this real round and the "father" round. Someone cheated and it's necessary to change the mode
                    if (round != 1 && round % 2 == 0 && sumOfO.equals(messagesSentInPreviousRounds.get(round / 2))) {
                        // Remove next round to happen (it will be a virtual round with no messages sent)
                        removeRoundToHappen(nextRoundsToHappen, round + 1);
                        // Change resending mode
                        if (room.getNonProbabilisticMode()) {
                            room.setNonProbabilisticMode(false);
                        }
                    }

                    /* RESENDING PROTOCOL **/
                    // Check if my message was involved in the collision, checking if in this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        // Non probabilistic mode (see Reference for more information)
                        if (room.getNonProbabilisticMode()) {
                            // Calculate average message, if my message is below that value i re-send in the round (2*round)
                            if (plainMessageWithRandomPadding.compareTo(sumOfM.divide(sumOfT)) <= 0) {
                                if (cheaterNode)
                                    nextRoundAllowedToSend = 2 * round + 1;
                                else
                                    nextRoundAllowedToSend = 2 * round;
                            }
                            // If not, i re-send my message in the round (2*round + 1)
                            else {
                                if (cheaterNode)
                                    nextRoundAllowedToSend = 2 * round;
                                else
                                    nextRoundAllowedToSend = 2 * round + 1;
                            }
                        }
                        // Probabilistic mode (see Reference for more information)
                        else {
                            // Throw a coin to see if a send in the round (2*round) or (2*round + 1)
                            boolean coin = new SecureRandom().nextBoolean();
                            if (coin)
                                nextRoundAllowedToSend = 2 * round;
                            else
                                nextRoundAllowedToSend = 2 * round + 1;
                        }
                    }
                    // Add (2*round) and (2*round + 1) rounds to future plays
                    addRoundToHappenNext(nextRoundsToHappen, 2 * round);
                    addRoundToHappenNext(nextRoundsToHappen, 2 * round + 1);
                }
            }
        }

        // Finish time measurement
        long t2 = System.nanoTime();
        // Save execution time
        executionTime = t2 - t1;
        // Save average time per message
        try {
            averageTimePerMessage = executionTime / messagesSentWithNoCollisions;
        } catch (ArithmeticException e) {
            averageTimePerMessage = 0;
        }
    }

    private int getNearestRealRound(int fatherRound) {
        int possibleNearestRound = (fatherRound - 1) / 2;
        while (!(possibleNearestRound % 2 == 0 || possibleNearestRound == 1))
            possibleNearestRound = (possibleNearestRound - 1) / 2;
        return possibleNearestRound;
    }

    private BigInteger calculateRandomForCommitmentOnMessage(BigInteger randomForPlainMessage, BigInteger randomForRandomPadding, BigInteger randomForFinalBit, Room room) {
        BigInteger two = BigInteger.valueOf(2);
        BigInteger nPlusOne = BigInteger.valueOf(room.getRoomSize() + 1);
        int randomPaddingLength = room.getPadLength() * 8; // z

        return randomForPlainMessage.multiply(two.pow(randomPaddingLength).multiply(nPlusOne)).add(randomForRandomPadding).multiply(nPlusOne).add(randomForFinalBit);

    }

    private BigInteger constructCommitmentOnMessage(BigInteger commitmentOnPlainMessage, BigInteger commitmentOnRandomPadding, BigInteger commitmentOnFinalBit, Room room) {
        BigInteger two = BigInteger.valueOf(2);
        BigInteger nPlusOne = BigInteger.valueOf(room.getRoomSize() + 1);
        int nPlusOneInteger = room.getRoomSize() + 1;
        int randomPaddingLength = room.getPadLength() * 8; // z

        return commitmentOnPlainMessage
                .modPow(two.pow(randomPaddingLength).multiply(nPlusOne), room.getP())
                .multiply(commitmentOnRandomPadding)
                .pow(nPlusOneInteger)
                .multiply(commitmentOnFinalBit)
                .mod(room.getP());

    }

    /**
     * @param sharedRandomValues array with all the shared random values used for commitments on key
     * @return sum of all values on sharedRandomValues
     */
    private BigInteger calculateRandomForCommitmentOnKey(BigInteger[] sharedRandomValues) {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger sharedRandomValue : sharedRandomValues) {
            result = result.add(sharedRandomValue);
        }
        return result;
    }

    /**
     * @param commitmentsOnKeys commitments in each individual round key (shared with another participant node)
     * @param room              Room where the participant node is playing
     * @return multiplication of each individual commitment: c = c_1*c_2*...*c_n (mod p)
     */
    private BigInteger generateCommitmentOnKey(BigInteger[] commitmentsOnKeys, Room room) {
        BigInteger _a = BigInteger.ONE;
        for (BigInteger commitmentsOnKey : commitmentsOnKeys) {
            _a = _a.multiply(commitmentsOnKey);
        }
        return _a.mod(room.getP());
    }

    /**
     * @return total execution time of this session
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     * @return time to get the first message of this session
     */
    public long getFirstMessageTime() {
        return firstMessageTime;
    }

    /**
     * @return average time per message of this session
     */
    public long getAverageTimePerMessage() {
        return averageTimePerMessage;
    }

    /**
     * @param nodeIndex  index of the participant node
     * @param repliers   array with zmq sockets that work as repliers
     * @param requestors array with zmq sockets that work as requestors
     * @param room       room where the messages are going send
     */
    private void synchronizeNodes(int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a message
                replier.recv(0);
                // When the replier receives the message, replies with another message
                replier.send("", 0);
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a message
                requestor.send("".getBytes(), 0);
                // The requestor waits to receive a reply by the correspondent replier
                requestor.recv(0);
            }
    }

    /**
     * Remove a round to happen afterwards
     *
     * @param nextRoundsToHappen list with rounds that are going to happen in the future
     * @param round              index of the round that wants to remove from happening
     */
    private void removeRoundToHappen(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.removeFirstOccurrence(round);
    }

    /**
     * Add one round to happen afterwards (is added at the end of the LinkedList)
     *
     * @param nextRoundsToHappen list with round that are going to happen in the future
     * @param round              index of the round to add
     */
    private void addRoundToHappenNext(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.add(round);
    }

    /**
     * Create all the repliers (the quantity depends on the index of the node) socket
     * necessary to run the protocol (see Reference for more information)
     *
     * @param nodeIndex index of the participant node
     * @param context   context where the zmq sockets are going to run
     */
    public void initializeRepliersArray(int nodeIndex, ZContext context) {
        // Create an array of sockets
        ZMQ.Socket[] repliers = null;
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1) {
            // Initialize the array with exactly (<nodeIndex> - 1) sockets
            repliers = new ZMQ.Socket[nodeIndex - 1];
            for (int i = 0; i < repliers.length; i++) {
                // Create the REP socket
                repliers[i] = context.createSocket(ZMQ.REP);
                // Bind this REP socket to the correspondent port in order to be connected by his correspondent REQ socket of another node
                repliers[i].bind("tcp://*:" + (7000 + i));
            }
        }
        // Return the array with the replier sockets
        this.repliers = repliers;
    }

    /**
     * Create all the requestors (the quantity depends on the index of the node) socket necessary to run the protocol
     * (see Reference for more information)
     *
     * @param nodeIndex index of the participant node
     * @param context   context where the zmq sockets are going to run
     * @param room      room where the messages are being sent
     */
    public void initializeRequestorsArray(int nodeIndex, ZContext context, Room room) {
        // Create an array of sockets
        ZMQ.Socket[] requestors = null;
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize()) {
            // Initialize the array with exactly (<n> - <nodeIndex>) sockets
            requestors = new ZMQ.Socket[room.getRoomSize() - nodeIndex];
            for (int i = 0; i < requestors.length; i++) {
                // Create the REQ socket
                requestors[i] = context.createSocket(ZMQ.REQ);
                // Connect this REQ socket to his correspondent REP socket of another node
                requestors[i].connect("tcp://" + room.getNodeIpFromIndex(nodeIndex + i + 1) + ":" + (7000 + nodeIndex - 1));
            }
        }
        // Return the array with the requestor sockets
        this.requestors = requestors;
    }

    /**
     * @return number of real rounds played in this session
     */
    public int getRealRoundsPlayed() {
        return realRoundsPlayed;
    }

    public long getTotalSyncTime() {
        return totalSyncTime;
    }

    /**
     * @param nodeIndex index of the participant node
     * @param roomSize  size of the room
     */
    public void closeRepliersAndRequestorsSockets(int nodeIndex, int roomSize) {
        if (nodeIndex != 1) {
            for (ZMQ.Socket replier : repliers)
                replier.close();
        }
        if (nodeIndex != roomSize) {
            for (ZMQ.Socket requestor : requestors)
                requestor.close();
        }
    }

}
