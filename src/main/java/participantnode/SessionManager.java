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
import java.util.*;

/**
 * Class that manages an entire session when running the DC-NET protocol.
 */
public class SessionManager {

    /**
     * Array of socket repliers of this participant node (one for each another node in the room)
     */
    private ZMQ.Socket[] repliers;

    /**
     * Array of socket requestors of this participant node (one for each another node in the room)
     */
    private ZMQ.Socket[] requestors;

    /**
     * Store how many real rounds were played in this session
     */
    private int realRoundsPlayed;

    /**
     * Total time that took the execution of this session
     */
    private long executionTime;

    /**
     * Time that took to receive the first message in this session
     */
    private long firstMessageTime;

    /**
     * Average time per message (total time divided by number of messages that went through)
     */
    private long averageTimePerMessage;

    /**
     * Time that took the synchronization of the nodes
     */
    private long totalSyncTime;

    /**
     * Initialize all parameters of SessionManager with default values
     */
    public SessionManager() {
        realRoundsPlayed = 0;
        executionTime = 0;
        firstMessageTime = 0;
        totalSyncTime = 0;
    }

    /**
     * Method that runs a single session for a single participant node within an specific room
     *
     * @param nodeIndex                index of the participant node provided by the directory node
     * @param participantMessage       string with the message that participant node wants to communicate
     * @param room                     room where the message is going to be send
     * @param node                     participant node
     * @param receiverThread           thread where participant node is listening to receive messages from
     *                                 the rest of the room
     * @param messagesList             where to store the messages that fo through the protocol
     * @param cheaterNode              true if the node cheats (send messages in the wrong rounds), false if not
     * @param observableMessageArrived observable that notifies when a message has arrived
     * @throws IOException              test
     * @throws NoSuchAlgorithmException test
     */
    public void runSession(int nodeIndex, String participantMessage, boolean cheaterNode, Room room,
                           ParticipantNode node, ZMQ.Socket receiverThread, ArrayList<String> messagesList,
                           DCNETProtocol.ObservableMessageArrived observableMessageArrived)
            throws IOException, NoSuchAlgorithmException {

        // Current round that is being played
        int currentRound;

        // Next round that the participant node is allow to send a message
        int nextRoundAllowedToSend = 1;

        // Size of the collision produced in the first round
        int collisionSize = 0;

        // Number of messages that went through the protocol. When this number equals collisionSize, the protocol
        // is over
        int messagesSentWithNoCollisions = 0;

        // Check if the message of this participant node was already transmitted
        boolean messageTransmitted = false;

        // Check if the protocol is over or not yet
        boolean finished = false;

        // Verify if the participant node will send a message in the current round or not
        boolean messageInThisRound;

        // Store messages that were sent in previous rounds in order to construct messages of virtual rounds
        Dictionary<Integer, BigInteger> messagesSentInPreviousRounds = new Hashtable<>();

        // Store which rounds will happen afterwards in the protocol
        LinkedList<Integer> nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);

        // Check for empty message
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

        // Set values of subsequently pedersen commitments with the public info of the room
        PedersenCommitment pedersenCommitment = new PedersenCommitment(room.getG(), room.getH(), room.getQ(), room.getP());

        // Initialize ZeroKnowledgeProof with values of the room
        ZeroKnowledgeProof zkp = new ZeroKnowledgeProof(nodeIndex);

        // Store commitments on plain message of current participant node
        Dictionary<Integer, BigInteger> commitmentsOnPlainMessage = new Hashtable<>();

        // Store random values for commitments on plain message of current participant node
        Dictionary<Integer, BigInteger> randomsForPlainMessage = new Hashtable<>();

        // Store commitments on plain messages of others participant nodes in the room
        List<Hashtable<Integer, BigInteger>> receivedCommitmentsOnPlainMessages = new ArrayList<>();
        for (int i = 0; i < room.getRoomSize(); i++) {
            receivedCommitmentsOnPlainMessages.add(i, new Hashtable<Integer, BigInteger>());
        }

        // Set time to measure entire session
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
                currentRound = nextRoundsToHappen.removeFirst();
                receiverThread.send("" + currentRound);
            }

            System.err.println("Current Round: " + currentRound);

            // Variables to store the resulting message of the round
            BigInteger sumOfM, sumOfT, sumOfO = BigInteger.ZERO;

            // Store commitments on keys and on message for future checking
            BigInteger[] receivedCommitmentsOnKeyCurrentRound = new BigInteger[room.getRoomSize()];
            BigInteger[] receivedCommitmentsOnMessageCurrentRound = new BigInteger[room.getRoomSize()];

            /* REAL ROUND (first and even rounds) */
            if (currentRound == 1 || currentRound % 2 == 0) {

                // Check if in this round the participant will send a real message or a zero message
                messageInThisRound = !messageTransmitted && nextRoundAllowedToSend == currentRound;

                // Add one to the count of real rounds played
                realRoundsPlayed++;

                /* KEY SHARING PART */
                // Initialize KeyGeneration
                /*KeyGeneration keyGeneration = new SecretSharing(room.getRoomSize(),
                nodeIndex, repliers, requestors, room);*/
                KeyGeneration keyGeneration = new DiffieHellman(room.getRoomSize() - 1, room.getG(), room.getP(),
                        nodeIndex, repliers, requestors, room);

                // Generate Participant Node values
                keyGeneration.generateParticipantNodeValues();

                // Get other participants values (to produce cancellation keys)
                keyGeneration.getOtherParticipantNodesValues();

                // Generation of the main key round value (operation over the shared key values)
                BigInteger keyRoundValue = keyGeneration.getParticipantNodeRoundKeyValue();

                /* SEND COMMITMENT AND POK ON KEY */
                // Get round keys (shared keys) of the current participant node
                BigInteger[] ownRoundKeysCurrentRound = keyGeneration.getRoundKeys();

                // Get shared random values
                BigInteger[] sharedRandomValuesCurrentRound = keyGeneration.getSharedRandomValues();

                // Calculate and save commitments on each round key
                BigInteger[] ownCommitmentsOnKeysCurrentRound = new BigInteger[ownRoundKeysCurrentRound.length];
                for (int i = 0; i < ownRoundKeysCurrentRound.length; i++)
                    ownCommitmentsOnKeysCurrentRound[i] = pedersenCommitment.calculateCommitment(
                            ownRoundKeysCurrentRound[i], sharedRandomValuesCurrentRound[i]);

                // Retrieve random for commitment on key
                BigInteger randomForCommitmentOnKeyCurrentRound = calculateRandomForCommitmentOnKey(
                        sharedRandomValuesCurrentRound);

                // Generate general commitment value for the resulting round key (operation over round keys)
                BigInteger ownCommitmentOnKeyCurrentRound = generateCommitmentOnKey(ownCommitmentsOnKeysCurrentRound,
                        room);

                // Generate proof of knowledge on key stored in commitment
                ProofOfKnowledgePedersen ownProofOfKnowledgeOnKey = zkp.generateProofOfKnowledgePedersen(
                        ownCommitmentOnKeyCurrentRound, room.getG(), keyRoundValue, room.getH(),
                        randomForCommitmentOnKeyCurrentRound, room.getQ(), room.getP());

                // Generate Json string containing commitmentOnKey and proofOfKnowledge
                CommitmentAndProofOfKnowledge ownCommitmentAndProofOfKnowledgeOnKey = new CommitmentAndProofOfKnowledge(
                        ownCommitmentOnKeyCurrentRound, ownProofOfKnowledgeOnKey);
                String ownCommitmentAndProofOfKnowledgeOnKeyJson = new Gson().toJson(
                        ownCommitmentAndProofOfKnowledgeOnKey, CommitmentAndProofOfKnowledge.class);

                // Send commitment on key and index to the room
                node.getSender().send(ownCommitmentAndProofOfKnowledgeOnKeyJson);

                /* RECEIVE COMMITMENTS AND POKs ON KEYS */
                BigInteger multiplicationOnCommitments = BigInteger.ONE;
                for (int i = 0; i < room.getRoomSize(); i++) {
                    // Wait response from Receiver thread as a string
                    String receivedCommitmentAndProofOfKnowledgeOnKeyJson = receiverThread.recvStr();

                    // Transform string (json) to CommitmentAndProofOfKnowledge object
                    CommitmentAndProofOfKnowledge receivedCommitmentAndProofOfKnowledgeOnKey = new Gson().fromJson(
                            receivedCommitmentAndProofOfKnowledgeOnKeyJson, CommitmentAndProofOfKnowledge.class);

                    // Get commitmentOnKey and index of the node that is sending the values
                    BigInteger receivedCommitmentOnKey = receivedCommitmentAndProofOfKnowledgeOnKey.getCommitment();
                    int receivedIndex = receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge().getNodeIndex();

                    // Store commitment for future checking
                    receivedCommitmentsOnKeyCurrentRound[receivedIndex - 1] = receivedCommitmentOnKey;

                    // Verify proofOfKnowledge
                    if (!zkp.verifyProofOfKnowledgePedersen(receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge(),
                            receivedCommitmentOnKey, room.getG(), room.getH(), room.getQ(), room.getP()))
                        System.err.println("WRONG PoK on Key. Round: " + currentRound + ", Node: " +
                                receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge().getNodeIndex());

                    // Calculate multiplication of incoming commitments
                    multiplicationOnCommitments = multiplicationOnCommitments.multiply(receivedCommitmentOnKey).
                            mod(room.getP());
                }
                // Check that multiplication result is 1
                if (!multiplicationOnCommitments.equals(BigInteger.ONE))
                    System.err.println("Round " + currentRound + " commitments on keys are WRONG");

                /* SET MESSAGES AND OBJECTS OF THIS ROUND */
                // Set protocol message to make a commitment to and add round key to the message
                // to construct Json that will be sent
                BigInteger ownProtocolRoundMessageCurrentRound;
                BigInteger ownPlainMessageCurrentRound, ownRandomPaddingCurrentRound, ownFinalBitCurrentRound;
                if (messageInThisRound) {
                    ownProtocolRoundMessageCurrentRound = outputParticipantMessage.getProtocolMessage();
                    outputParticipantMessage.setRoundKeyValue(keyRoundValue);
                    ownPlainMessageCurrentRound = outputParticipantMessage.getPlainMessage();
                    ownRandomPaddingCurrentRound = outputParticipantMessage.getRandomPadding();
                    ownFinalBitCurrentRound = outputParticipantMessage.getFinalBit();
                } else {
                    ownProtocolRoundMessageCurrentRound = BigInteger.ZERO;
                    zeroMessage.setRoundKeyValue(keyRoundValue);
                    ownPlainMessageCurrentRound = zeroMessage.getPlainMessage();
                    ownRandomPaddingCurrentRound = zeroMessage.getRandomPadding();
                    ownFinalBitCurrentRound = zeroMessage.getFinalBit();
                }

                /* SEND CORRECT FORMAT OF MESSAGE PROOF */
                // Random values
                BigInteger randomForCommitmentOnPlainMessage = pedersenCommitment.generateRandom();
                BigInteger randomForCommitmentOnRandomPadding = pedersenCommitment.generateRandom();
                BigInteger randomForCommitmentOnFinalBit = pedersenCommitment.generateRandom();

                // Store random for commitment on plain message for future use
                randomsForPlainMessage.put(currentRound, randomForCommitmentOnPlainMessage);

                // Commitments for single values
                BigInteger commitmentOnPlainMessage = pedersenCommitment.calculateCommitment(
                        ownPlainMessageCurrentRound, randomForCommitmentOnPlainMessage);
                BigInteger commitmentOnRandomPadding = pedersenCommitment.calculateCommitment(
                        ownRandomPaddingCurrentRound, randomForCommitmentOnRandomPadding);
                BigInteger commitmentOnFinalBit = pedersenCommitment.calculateCommitment(
                        ownFinalBitCurrentRound, randomForCommitmentOnFinalBit);

                // Store commitment on plain message for future use
                commitmentsOnPlainMessage.put(currentRound, commitmentOnPlainMessage);

                // Create Object with single commitments
                CommitmentsOnSingleValues commitmentsOnSingleValues = new CommitmentsOnSingleValues(
                        commitmentOnPlainMessage, commitmentOnRandomPadding, commitmentOnFinalBit, nodeIndex);

                // Create Proof that the format of the message is correct
                ProofOfKnowledgeMessageFormat ownProofForMessageFormat;
                BigInteger _comm = room.getG().modInverse(room.getP()).multiply(
                        commitmentOnFinalBit).mod(room.getP()); // _comm = g^{-1} * C_b (mod p)
                if (messageInThisRound && !emptyMessage) {
                    ownProofForMessageFormat = zkp.generateProofOfKnowledgeMessageFormatX1(
                            _comm, room.getH(), randomForCommitmentOnFinalBit, commitmentOnFinalBit,
                            commitmentOnPlainMessage, room.getQ(), room.getP());
                } else {
                    ownProofForMessageFormat = zkp.generateProofOfKnowledgeMessageFormatX2X3(
                            _comm, room.getH(), commitmentOnFinalBit, randomForCommitmentOnFinalBit,
                            commitmentOnPlainMessage, randomForCommitmentOnPlainMessage, room.getQ(), room.getP());
                }

                // Generate Json string containing commitment and proof that the format of the message is correct
                CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat
                        commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat =
                        new CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat(
                                commitmentsOnSingleValues, ownProofForMessageFormat);
                String commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormatJson =
                        new Gson().toJson(
                                commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat,
                                CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat.class);

                // Send commitment and Proof of Knowledge that the format of the message is correct
                node.getSender().send(commitmentsOnSingleValuesAndProofOfKnowledgeMessageFormatJson);

                /* RECEIVE COMMITMENTS ON SINGLE VALUES AND POK ON CORRECT MESSAGE FORMAT */
                for (int i = 0; i < room.getRoomSize(); i++) {
                    // Wait response from Receiver thread as a string
                    String receivedCommitmentsOnSingleValuesAndPOKMessageFormatJson = receiverThread.recvStr();

                    // Transform string (json) to CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat object
                    CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat
                            receivedCommitmentsOnSingleValuesAndPOKMessageFormat = new Gson().fromJson(
                            receivedCommitmentsOnSingleValuesAndPOKMessageFormatJson,
                            CommitmentsOnSingleValuesAndProofOfKnowledgeMessageFormat.class);

                    // Get commitmentOnPlainMessage, commitmentOnRandomPadding, receivedCommitmentOnFinalBit
                    // and index of the node that is sending the values
                    CommitmentsOnSingleValues receivedCommitmentsOnSingleKeys =
                            receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getCommitmentsOnSingleValues();
                    BigInteger receivedCommitmentOnPlainMessage =
                            receivedCommitmentsOnSingleKeys.getCommitmentOnPlainMessage();
                    BigInteger receivedCommitmentOnRandomPadding =
                            receivedCommitmentsOnSingleKeys.getCommitmentOnRandomPadding();
                    BigInteger receivedCommitmentOnFinalBit =
                            receivedCommitmentsOnSingleKeys.getCommitmentOnFinalBit();
                    int participantNodeIndex = receivedCommitmentsOnSingleKeys.getNodeIndex();

                    // Store received commitment on plain message for future use in subsequent rounds
                    receivedCommitmentsOnPlainMessages.get(participantNodeIndex - 1).
                            put(currentRound, receivedCommitmentOnPlainMessage);

                    // Construct received commitment on message using received commitments on single values
                    BigInteger receivedCommitmentOnMessage = constructCommitmentOnMessage(
                            receivedCommitmentOnPlainMessage, receivedCommitmentOnRandomPadding,
                            receivedCommitmentOnFinalBit, room);

                    // Store received commitment on message for future use in this round
                    receivedCommitmentsOnMessageCurrentRound[participantNodeIndex - 1] = receivedCommitmentOnMessage;

                    // Get proof that the format of the message received is correct
                    ProofOfKnowledgeMessageFormat receivedProofForMessageFormat =
                            receivedCommitmentsOnSingleValuesAndPOKMessageFormat.getProofOfKnowledgeMessageFormat();

                    // Verify Proof of Knowledge
                    BigInteger _rcvComm = room.getG().modInverse(room.getP()).multiply(
                            receivedCommitmentOnFinalBit).mod(room.getP()); // _comm = g^{-1} * C_b
                    if (!zkp.verifyProofOfKnowledgeMessageFormat(receivedProofForMessageFormat, _rcvComm,
                            receivedCommitmentOnFinalBit, receivedCommitmentOnPlainMessage,
                            room.getH(), room.getQ(), room.getP()))
                        System.err.println("WRONG PoK on Message Format. Round: " + currentRound + ", Node: " +
                                receivedProofForMessageFormat.getNodeIndex());
                }

                /* SEND POK ON MESSAGE */
                // Generate Commitment on message using commitments on single values created previously
                BigInteger ownCommitmentOnMessage = constructCommitmentOnMessage(commitmentOnPlainMessage,
                        commitmentOnRandomPadding, commitmentOnFinalBit, room);

                // Generate random value for commitment using random for commitments on single values created previously
                BigInteger ownRandomForCommitmentOnMessage = calculateRandomForCommitmentOnMessage(
                        randomForCommitmentOnPlainMessage, randomForCommitmentOnRandomPadding,
                        randomForCommitmentOnFinalBit, room);

                // Generate ProofOfKnowledgePedersen associated with the commitment for the protocol message
                ProofOfKnowledgePedersen proofOfKnowledgeOnMessage = zkp.generateProofOfKnowledgePedersen(
                        ownCommitmentOnMessage, room.getG(), ownProtocolRoundMessageCurrentRound,
                        room.getH(), ownRandomForCommitmentOnMessage, room.getQ(), room.getP());
                String proofOfKnowledgeOnMessageJson = new Gson().toJson(
                        proofOfKnowledgeOnMessage, ProofOfKnowledgePedersen.class);

                // Send Json to the room (which contains the proofOfKnowledge)
                node.getSender().send(proofOfKnowledgeOnMessageJson);

                /* RECEIVE COMMITMENTS AND POKs ON MESSAGES */
                for (int i = 0; i < room.getRoomSize(); i++) {
                    // Wait response from Receiver thread as a String (json)
                    String receivedProofOfKnowledgeOnMessageJson = receiverThread.recvStr();

                    // Transform String (json) to object ProofOfKnowledgePedersen
                    ProofOfKnowledgePedersen receivedProofOfKnowledgeOnMessage = new Gson().fromJson(
                            receivedProofOfKnowledgeOnMessageJson, ProofOfKnowledgePedersen.class);
                    int receivedNodeIndex = receivedProofOfKnowledgeOnMessage.getNodeIndex();

                    // Verify proof of knowledge
                    if (!zkp.verifyProofOfKnowledgePedersen(receivedProofOfKnowledgeOnMessage,
                            receivedCommitmentsOnMessageCurrentRound[receivedNodeIndex - 1],
                            room.getG(), room.getH(), room.getQ(), room.getP()))
                        System.err.println("WRONG PoK on Message. Round: " + currentRound + ", Node: " +
                                receivedProofOfKnowledgeOnMessage.getNodeIndex());
                }

                /* SEND OUTPUT MESSAGE AND POK ASSOCIATED */
                // Set Proof of Knowledge that is needed for Round 1
                if (currentRound == 1) {
                    // Calculate random for commitment as the sum of both random values used before
                    // (for commitment on key and for commitment on message)
                    BigInteger randomForCommitmentOnOutputMessage = randomForCommitmentOnKeyCurrentRound.add(
                            ownRandomForCommitmentOnMessage);

                    // Commitment for the sum of both randomness used
                    BigInteger commitmentOnSumOfRandomness = new Commitment(
                            room.getH(), room.getQ(), room.getP()).calculateCommitment(
                            randomForCommitmentOnOutputMessage);

                    // Generate proofOfKnowledge for OutputMessage, as a commitment for the sum of both randomness used
                    ProofOfKnowledge proofOfKnowledgeOnOutputMessage = zkp.generateProofOfKnowledge(
                            commitmentOnSumOfRandomness, room.getH(), randomForCommitmentOnOutputMessage,
                            room.getQ(), room.getP());

                    // Generate Json string with Object containing both outputMessage and proofOfKnowledge
                    OutputMessageAndProofOfKnowledge outputMessageAndProofOfKnowledge =
                            new OutputMessageAndProofOfKnowledge(outputParticipantMessage,
                                    proofOfKnowledgeOnOutputMessage);
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(
                            outputMessageAndProofOfKnowledge, OutputMessageAndProofOfKnowledge.class);

                    // Send the Json to the room (which contains the outputMessage and the proofOfKnowledge)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                }

                // Set Proof of Knowledge that is needed for rounds which have a father round real
                else if ((currentRound / 2) % 2 == 0 || currentRound == 2) {
                    // Calculate commitment on plain message of father round divided by
                    // commitment on plain message of current round
                    BigInteger divisionOfCommitments = commitmentOnPlainMessage.modInverse(room.getP()).multiply(
                            commitmentsOnPlainMessage.get((currentRound / 2)));

                    // Create Pok depending if the participant node will send a message in this round or not
                    ProofOfKnowledgeResendingFatherRoundReal proofOfKnowledgeResendingFatherRoundReal;
                    OutputMessageAndProofOfKnowledgeResendingFatherRoundReal
                            outputMessageAndProofOfKnowledgeResendingFatherRoundReal;

                    // If will send a message, needs to prove that is the same message that was sent in the father round
                    if (messageInThisRound) {
                        // Calculate subtraction of randomness used for commitments on plain message sent in
                        // the current round and in the father round
                        BigInteger subtractionOfRandomness = randomsForPlainMessage.get((currentRound / 2)).subtract(
                                randomForCommitmentOnPlainMessage).mod(room.getQ());

                        // Create Pok and create object containing it and the output message
                        proofOfKnowledgeResendingFatherRoundReal =
                                zkp.generateProofOfKnowledgeResendingFatherRoundRealX2(
                                        commitmentOnPlainMessage, divisionOfCommitments, room.getH(),
                                        subtractionOfRandomness, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundReal =
                                new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(
                                        outputParticipantMessage, proofOfKnowledgeResendingFatherRoundReal);
                    }

                    // If won't, needs to prove that his message is zero
                    else {
                        // Create Pok and create object containing it and the output message
                        proofOfKnowledgeResendingFatherRoundReal =
                                zkp.generateProofOfKnowledgeResendingFatherRoundRealX1(
                                        commitmentOnPlainMessage, room.getH(), randomForCommitmentOnPlainMessage,
                                        divisionOfCommitments, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundReal =
                                new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(
                                        zeroMessage, proofOfKnowledgeResendingFatherRoundReal);
                    }

                    // Generate Json object with the Pok (using an OR) and the output message
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(
                            outputMessageAndProofOfKnowledgeResendingFatherRoundReal,
                            OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);

                    // Send Json to the room (containing the output Message and the Pok when the father round is real)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                }

                // Set Proof of Knowledge that is needed for rounds which have a father round virtual
                else {
                    // Calculate number of the father round (which is virtual) and the nearest real round
                    // (between the current round and the first round)
                    int virtualFatherRound = (currentRound / 2);
                    int nearestRealRound = getNearestRealRound(virtualFatherRound);

                    // Get real rounds between current and nearest real round
                    int[] realRounds = convertToIntArray(getRealRoundsToCheckNotSending(nearestRealRound,
                            virtualFatherRound));

                    BigInteger[] commitmentsOnPlainMessagesInPreviousRounds = new BigInteger[realRounds.length];
                    for (int i = 0; i < realRounds.length; i++) {
                        commitmentsOnPlainMessagesInPreviousRounds[i] = commitmentsOnPlainMessage.get(realRounds[i]);
                    }
                    // TODO: message not sent in real rounds between this and that previous one

                    // Calculate commitment on plain message of nearest real round divided by
                    // commitment on plain message of current round
                    BigInteger divisionOfCommitments = commitmentOnPlainMessage.modInverse(room.getP()).multiply(
                            commitmentsOnPlainMessage.get(nearestRealRound));

                    // Create Pok depending if the participant node will send a message in this round or not
                    ProofOfKnowledgeResendingFatherRoundReal proofOfKnowledgeResendingFatherRoundVirtual;
                    OutputMessageAndProofOfKnowledgeResendingFatherRoundReal
                            outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual;

                    // If will send a message, needs to prove that is the same message that was sent in the nearest real round
                    if (messageInThisRound) {
                        // Calculate subtraction of randomness used for commitments on plain message sent in
                        // the current round and in the nearest real round
                        BigInteger subtractionOfRandomness = randomsForPlainMessage.get(nearestRealRound).subtract(
                                randomForCommitmentOnPlainMessage).mod(room.getQ());

                        // Create Pok and create object containing it and the output message
                        proofOfKnowledgeResendingFatherRoundVirtual =
                                zkp.generateProofOfKnowledgeResendingFatherRoundRealX2(
                                        commitmentOnPlainMessage, divisionOfCommitments, room.getH(),
                                        subtractionOfRandomness, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual =
                                new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(
                                        outputParticipantMessage, proofOfKnowledgeResendingFatherRoundVirtual);
                    }

                    // If won't, needs to prove that the message is zero
                    else {
                        // Create Pok and create object containing it and the output message
                        proofOfKnowledgeResendingFatherRoundVirtual =
                                zkp.generateProofOfKnowledgeResendingFatherRoundRealX1(
                                        commitmentOnPlainMessage, room.getH(), randomForCommitmentOnPlainMessage,
                                        divisionOfCommitments, room.getQ(), room.getP());
                        outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual =
                                new OutputMessageAndProofOfKnowledgeResendingFatherRoundReal(
                                        zeroMessage, proofOfKnowledgeResendingFatherRoundVirtual);
                    }

                    // Generate Json object with the Pok (using an OR) and the output message
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(
                            outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual,
                            OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);

                    // Send Json to the room (containing the output Message and the Pok when the father round is virtual)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                }

                // Subtract round key to the message in order to send a clear one in the next round
                if (messageInThisRound)
                    outputParticipantMessage.setRoundKeyValue(keyRoundValue.negate());
                else
                    zeroMessage.setRoundKeyValue(keyRoundValue.negate());

                /* RECEIVE OUTPUT MESSAGES AND POKs ASSOCIATED */
                // Receive Pok that is needed for Round 1
                if (currentRound == 1) {
                    // Variable to count how many messages were received from the receiver thread in this round
                    int messagesReceivedInThisRound = 0;

                    // When this number equals the total number of participants nodes in the room,
                    // it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {

                        // Receive a message (json) from receiver thread
                        String messageReceivedFromReceiverThread = receiverThread.recvStr();

                        // Transform incoming message (json) to a OutputMessageAndProofOfKnowledge object
                        OutputMessageAndProofOfKnowledge outputMessageAndProofOfKnowledge = new Gson().fromJson(
                                messageReceivedFromReceiverThread, OutputMessageAndProofOfKnowledge.class);

                        // Get index of participant node that is sending his proofOfKnowledge
                        int participantNodeIndex = outputMessageAndProofOfKnowledge.getProofOfKnowledge().getNodeIndex();

                        // Construct commitment on outputMessage as the multiplication of
                        // commitmentOnKey and commitmentOnMessage
                        BigInteger commitmentOnOutputMessage =
                                receivedCommitmentsOnKeyCurrentRound[participantNodeIndex - 1].multiply(
                                        receivedCommitmentsOnMessageCurrentRound[participantNodeIndex - 1]).mod(
                                        room.getP());

                        // Construct beta using commitment on output message construct before
                        // in order to verify proof of knowledge sent by the participant node
                        BigInteger beta = commitmentOnOutputMessage.multiply(room.getG().modPow(
                                outputMessageAndProofOfKnowledge.getOutputMessage().getProtocolMessage(), room.getP())
                                .modInverse(room.getP())).mod(room.getP());

                        // Verify the proof of knowledge
                        if (!zkp.verifyProofOfKnowledge(outputMessageAndProofOfKnowledge.getProofOfKnowledge(),
                                beta, room.getH(), room.getQ(), room.getP()))
                            System.err.println("WRONG PoK on OutputMessage. Round: " + currentRound + ", Node: " +
                                    participantNodeIndex);

                        // Sum this incoming message with the rest that i've received in this round
                        // in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledge.getOutputMessage().getProtocolMessage())
                                .mod(room.getP());

                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                }

                // Receive Pok that is needed for rounds which have father round is real
                else if ((currentRound / 2) % 2 == 0 || currentRound == 2) {
                    // Variable to count how many messages were received from the receiver thread
                    int messagesReceivedInThisRound = 0;

                    // When this number equals the total number of participants nodes in the room,
                    // it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {

                        // Receive a message (json) from receiver thread
                        String messageReceivedFromReceiverThread = receiverThread.recvStr();

                        // Transform incoming message (json) to a
                        // OutputMessageAndProofOfKnowledgeResendingFatherRoundReal object
                        OutputMessageAndProofOfKnowledgeResendingFatherRoundReal
                                outputMessageAndProofOfKnowledgeResendingFatherRoundReal =
                                new Gson().fromJson(messageReceivedFromReceiverThread,
                                        OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);

                        // Get index of participant node that is sending his proofOfKnowledge
                        int participantNodeIndex = outputMessageAndProofOfKnowledgeResendingFatherRoundReal.
                                getProofOfKnowledgeResendingFatherRoundReal().getNodeIndex();

                        // Retrieve commitments on plain message sent in the current round and in the father round
                        BigInteger commitmentOnPlainMessageNodeRound2K = receivedCommitmentsOnPlainMessages.get(
                                participantNodeIndex - 1).get(currentRound);
                        BigInteger commitmentOnPlainMessageNodeRoundK = receivedCommitmentsOnPlainMessages.get(
                                participantNodeIndex - 1).get(currentRound / 2);

                        // Construct a commitment needed to verify Pok as the multiplication of the inverse of the
                        // commitment send in the current round with the commitment sent in the father round
                        BigInteger resultantCommitment = commitmentOnPlainMessageNodeRound2K.modInverse(room.getP())
                                .multiply(commitmentOnPlainMessageNodeRoundK);

                        // Verify proof of knowledge
                        if (!zkp.verifyProofOfKnowledgeResendingFatherRoundReal(
                                outputMessageAndProofOfKnowledgeResendingFatherRoundReal.
                                        getProofOfKnowledgeResendingFatherRoundReal(), commitmentOnPlainMessageNodeRound2K,
                                resultantCommitment, room.getH(), room.getQ(), room.getP()))
                            System.err.println("WRONG PoK on Resending when father round is real. Round: " +
                                    currentRound + ", Node: " + participantNodeIndex);

                        // Sum this incoming message with the rest that i've received in this round
                        // in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledgeResendingFatherRoundReal.getOutputMessage()
                                .getProtocolMessage()).mod(room.getP());

                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                }

                // Receive Pok that is needed for rounds which have father round is virtual
                else {
                    // Variable to count how many messages were received from the receiver thread
                    int messagesReceivedInThisRound = 0;

                    // When this number equals the total number of participants nodes in the room,
                    // it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {

                        // Receive a message (json) from receiver thread
                        String messageReceivedFromReceiverThread = receiverThread.recvStr();

                        // Transform incoming message (json) to a
                        // OutputMessageAndProofOfKnowledgeResendingFatherRoundReal object
                        OutputMessageAndProofOfKnowledgeResendingFatherRoundReal
                                outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual =
                                new Gson().fromJson(messageReceivedFromReceiverThread,
                                        OutputMessageAndProofOfKnowledgeResendingFatherRoundReal.class);

                        // Get index of participant node that is sending his proofOfKnowledge
                        int participantNodeIndex = outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual.
                                getProofOfKnowledgeResendingFatherRoundReal().getNodeIndex();

                        // Calculate the nearest real round played between the current and the first ones
                        int nearestRealRound = getNearestRealRound(currentRound / 2);

                        // Retrieve commitments on plain message sent in the current round and in the nearest real round
                        BigInteger commitmentOnPlainMessageNodeRound2K = receivedCommitmentsOnPlainMessages.
                                get(participantNodeIndex - 1).get(currentRound);
                        BigInteger commitmentOnPlainMessageNodeNearestRealRound = receivedCommitmentsOnPlainMessages.
                                get(participantNodeIndex - 1).get(nearestRealRound);

                        // Construct a commitment needed to verify Pok as the multiplication of the inverse of the
                        // commitment send in the current round with the commitment sent in the nearest real round
                        BigInteger resultantCommitment = commitmentOnPlainMessageNodeRound2K.modInverse(room.getP())
                                .multiply(commitmentOnPlainMessageNodeNearestRealRound);

                        // Verify proof of knowledge
                        if (!zkp.verifyProofOfKnowledgeResendingFatherRoundReal(
                                outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual.
                                        getProofOfKnowledgeResendingFatherRoundReal(),
                                commitmentOnPlainMessageNodeRound2K, resultantCommitment,
                                room.getH(), room.getQ(), room.getP()))
                            System.err.println("WRONG PoK on Resending when father round is virtual. Round: " +
                                    currentRound + ", Node: " + participantNodeIndex);

                        // Sum this incoming message with the rest that i've received in this round
                        // in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledgeResendingFatherRoundVirtual.
                                getOutputMessage().getProtocolMessage()).mod(room.getP());

                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                }
            }

            /* VIRTUAL ROUND (odd rounds) */
            else {
                // Recover messages sent in father and previous round in order to construct
                // the resulting message of this round
                BigInteger sumOfOSentInRound2K = messagesSentInPreviousRounds.get(currentRound - 1);
                BigInteger sumOfOSentInRoundK = messagesSentInPreviousRounds.get((currentRound - 1) / 2);

                // Construct the resulting message of this round
                sumOfO = sumOfOSentInRoundK.subtract(sumOfOSentInRound2K);
            }

            /* EXTRACT ROUND MESSAGES VALUES */
            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(currentRound, sumOfO);

            // Separate sumOfO in (sumOfM, sumOfT)
            sumOfM = sumOfO.divide(BigInteger.valueOf(room.getRoomSize() + 1));
            sumOfT = sumOfO.subtract(sumOfM.multiply(BigInteger.valueOf(room.getRoomSize() + 1)));
            System.err.println("Number of messages: " + sumOfT);

            // If we are playing the first round, assign the size of the collision
            if (currentRound == 1) {
                collisionSize = Integer.parseInt(sumOfT.toString());

                // If the size is 0, it means that no messages were sent during this session, so we finish the protocol
                if (collisionSize == 0) {
                    System.err.println("NO MESSAGES WERE SENT");
                    finished = true;
                    continue;
                }
            }

            /* NO COLLISION ROUND */
            // <sumOfT> = 1 => No Collision Round => a message went through, received by the rest of the nodes
            if (sumOfT.equals(BigInteger.ONE)) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // Freeze the time of receiving the first message (if it's indeed the first)
                if (messagesSentWithNoCollisions == 1)
                    firstMessageTime = System.nanoTime() - t1;

                // Retrieve message that went through the protocol
                String singleMessage = OutputMessage.getMessageWithoutRandomPadding(sumOfM, room);

                // Add message to the list
                messagesList.add(singleMessage);

                // Set message to Observable object to notify that a message went through
                observableMessageArrived.setValue(singleMessage);

                // If the message that went through is from current participant node,
                // it needs to set the variable in order to start sending zero messages in subsequent rounds
                if (plainMessageWithRandomPadding.equals(sumOfM))
                    messageTransmitted = true;

                // If the number of messages that went through until now equals the collision size,
                // the first collision was completely resolved.
                // It needs to be set a variable to finalize the protocol in the next round
                if (messagesSentWithNoCollisions == collisionSize)
                    finished = true;

            }

            // Size of collision is not zero
            else {
                /* PROBLEMATIC ROUND **/
                // <sumOfT> == 0 => if we are in a deterministic mode, this means that someone cheated,
                // and it's necessary to change the mode
                if (sumOfT.equals(BigInteger.ZERO)) {
                    // Change resending mode
                    if (room.getNonProbabilisticMode()) {
                        room.setNonProbabilisticMode(false);
                    }
                }

                /* COLLISION ROUND */
                // <sumOfT> > 1 => A Collision was produced
                if (sumOfT.compareTo(BigInteger.ONE) > 0) {

                    /* PROBLEMATIC ROUND */
                    // <sumOfT> gets repeated in this real round and the father round.
                    // Someone cheated and it's necessary to change the mode
                    if (currentRound != 1 && currentRound % 2 == 0 && sumOfO.equals(
                            messagesSentInPreviousRounds.get(currentRound / 2))) {
                        // Remove next round to happen (it will be a virtual round with no messages sent)
                        removeRoundToHappen(nextRoundsToHappen, currentRound + 1);

                        // Change resending mode
                        if (room.getNonProbabilisticMode()) {
                            room.setNonProbabilisticMode(false);
                        }
                    }

                    /* RESENDING PROTOCOL */
                    // Check if current participant node's message was involved in the collision,
                    // checking if in this round it was allowed to send a message
                    if (nextRoundAllowedToSend == currentRound) {

                        // Non probabilistic mode
                        if (room.getNonProbabilisticMode()) {

                            // Calculate average message, if the message is below that value
                            // it will be re-send in the round (2*round)
                            BigInteger averageMessage = sumOfM.divide(sumOfT);
                            if (plainMessageWithRandomPadding.compareTo(averageMessage) <= 0) {
                                if (cheaterNode)
                                    nextRoundAllowedToSend = 2 * currentRound + 1;
                                else
                                    nextRoundAllowedToSend = 2 * currentRound;
                            }

                            // If it's above the average, it will be re-send in the round (2*round + 1)
                            else {
                                if (cheaterNode)
                                    nextRoundAllowedToSend = 2 * currentRound;
                                else
                                    nextRoundAllowedToSend = 2 * currentRound + 1;
                            }
                        }

                        // Probabilistic mode
                        else {
                            // Throw a coin to see if the message is re-send in the round (2*round) or (2*round + 1)
                            boolean coin = new SecureRandom().nextBoolean();
                            if (coin)
                                nextRoundAllowedToSend = 2 * currentRound;
                            else
                                nextRoundAllowedToSend = 2 * currentRound + 1;
                        }
                    }

                    // Add (2*round) and (2*round + 1) rounds to future plays
                    addRoundToHappenNext(nextRoundsToHappen, 2 * currentRound);
                    addRoundToHappenNext(nextRoundsToHappen, 2 * currentRound + 1);
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

    private int[] convertToIntArray(Object[] array) {
        int[] a = new int[array.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = (int) array[i];
        }
        return a;
    }

    /**
     * Calculate nearest real round between fatherRound and the first round of the session
     *
     * @param fatherRound number of round that needs to be found the nearest real round
     * @return nearest real round between fatherRound and the first round of the session
     */
    private int getNearestRealRound(int fatherRound) {
        int possibleNearestRound = (fatherRound - 1) / 2;
        while (!(possibleNearestRound % 2 == 0 || possibleNearestRound == 1))
            possibleNearestRound = (possibleNearestRound - 1) / 2;
        return possibleNearestRound;
    }

    /**
     * Calculate the real rounds between the father of the current and the nearest real round
     *
     * @param nearestRealRound nearest real round between current round and round one
     * @param fatherVirtualRound father round (which is virtual) of the current round
     * @return real rounds between current and nearest real round in the direct branch
     */
    private Object[] getRealRoundsToCheckNotSending(int nearestRealRound, int fatherVirtualRound) {
        ArrayList<Integer> realRounds = new ArrayList<>();
        int auxRound = nearestRealRound;
        while(auxRound != fatherVirtualRound) {
            realRounds.add(auxRound*2);
            auxRound = 2*auxRound + 1;
        }
        return realRounds.toArray();
    }

    /**
     * @param randomForPlainMessage  random value for commitment on plain message
     * @param randomForRandomPadding random value for commitment on random padding
     * @param randomForFinalBit      random value for commitment on final bit
     * @param room                   room where the messages are being send
     * @return random for commitment on message
     */
    private BigInteger calculateRandomForCommitmentOnMessage(BigInteger randomForPlainMessage,
                                                             BigInteger randomForRandomPadding,
                                                             BigInteger randomForFinalBit, Room room) {
        BigInteger two = BigInteger.valueOf(2);
        BigInteger nPlusOne = BigInteger.valueOf(room.getRoomSize() + 1);
        int randomPaddingLength = room.getPadLength() * 8; // z

        return randomForPlainMessage.multiply(two.pow(randomPaddingLength).multiply(nPlusOne)).add(randomForRandomPadding).multiply(nPlusOne).add(randomForFinalBit);

    }

    /**
     * @param commitmentOnPlainMessage  commitment on plain message
     * @param commitmentOnRandomPadding commitment on random padding
     * @param commitmentOnFinalBit      commitment on final bit
     * @param room                      room where the messages are being send
     * @return commitment on message
     */
    private BigInteger constructCommitmentOnMessage(BigInteger commitmentOnPlainMessage,
                                                    BigInteger commitmentOnRandomPadding,
                                                    BigInteger commitmentOnFinalBit, Room room) {
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

    /**
     * @return total time that took the synchronization between the nodes
     */
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
