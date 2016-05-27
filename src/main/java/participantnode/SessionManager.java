package participantnode;

import com.google.gson.Gson;
import crypto.PedersenCommitment;
import crypto.ZeroKnowledgeProof;
import dcnet.Room;
import json.CommitmentAndProofOfKnowledge;
import json.OutputMessageAndProofOfKnowledge;
import json.ProofOfKnowledge;
import json.ProofOfKnowledgePedersen;
import keygeneration.DiffieHellman;
import keygeneration.KeyGeneration;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
    private long executionTime, firstMessageTime, averageTimePerMessage;

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
    }

    /**
     * Method that runs a single session for a single participant node within an specific room
     * @param nodeIndex index of the participant node
     * @param participantMessage string with the message that participant node wants to communicate
     * @param room room where the message is going to be send
     * @param node participant node
     * @param receiverThread thread where participant node is listening
     */
    public void runSession(int nodeIndex, String participantMessage, boolean cheaterNode, Room room, ParticipantNode node, ZMQ.Socket receiverThread, PrintStream out) throws IOException, NoSuchAlgorithmException {

        if (participantMessage.equals(""))
            participantMessage = "0";

        // Create an outputMessage
        OutputMessage outputParticipantMessage = new OutputMessage();
        outputParticipantMessage.setPaddingLength(room.getPadLength());
        outputParticipantMessage.setParticipantMessage(participantMessage, room);
        String outputParticipantMessageJson;

        // Create a zeroMessage
        OutputMessage zeroMessage = new OutputMessage();
        zeroMessage.setParticipantMessage("0", room);
        String zeroMessageJson;

        // Sleep to overlap slow joiner problem
        // TODO: fix this using a better solution
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Set values of subsequently commitments with the public info of the Room
        pedersenCommitment = new PedersenCommitment(room.getG(), room.getH(), room.getQ(), room.getP());

        // Initialize ZeroKnowledgeProof with values of the room
        ZeroKnowledgeProof zkp = new ZeroKnowledgeProof(room.getG(), room.getH(), room.getQ(), room.getP(), nodeIndex);

        // Set time to measure entire protocol
        long t1 = System.nanoTime();

        /** ROUNDS **/
        // Each loop of this while is a different round
        while (!Thread.currentThread().isInterrupted()) {
            // Synchronize nodes at the beginning of each round
            synchronizeNodes(nodeIndex, repliers, requestors, room);

            /* Check if the protocol was finished in the last round played.
             * If it so, let know to the receiver thread, wait for his response and break the loop */
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

            /** REAL ROUND (first and even rounds) **/
            if (round == 1 || round%2 == 0) {

                // Check if in this round the participant will send a real message or a zero message
                messageInThisRound = !messageTransmitted && nextRoundAllowedToSend == round;

                // Set variable that we are playing a real round and add one to the count
                realRoundsPlayed++;

                /** KEY SHARING PART **/
                // Initialize KeyGeneration
                /*KeyGeneration keyGeneration = new SecretSharing(room.getRoomSize(), nodeIndex, repliers, requestors, room);*/
                KeyGeneration keyGeneration = new DiffieHellman(room.getRoomSize() - 1, room.getG(), room.getP(), nodeIndex, repliers, requestors, room);
                // Generate Participant Node values
                keyGeneration.generateParticipantNodeValues();
                // Get other participants values (to produce cancellation keys)
                keyGeneration.getOtherParticipantNodesValues();
                // Generation of the main key round value (operation over the shared key values)
                BigInteger keyRoundValue = keyGeneration.getParticipantNodeRoundKeyValue();

                // Synchronize nodes to let know that we all finish the Key-Sharing part
                synchronizeNodes(nodeIndex, repliers, requestors, room);

                /** SEND COMMITMENT AND POK ON KEY **/
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
                ProofOfKnowledgePedersen proofOfKnowledgeOnKey = zkp.generateProofOfKnowledgePedersen(keyRoundValue, randomRoundValue);
                // Generate Json string containing commitmentOnKey and proofOfKnowledge
                CommitmentAndProofOfKnowledge commitmentAndProofOfKnowledgeOnKey = new CommitmentAndProofOfKnowledge(commitmentOnKey, proofOfKnowledgeOnKey);
                String commitmentAndProofOfKnowledgeOnKeyJson = new Gson().toJson(commitmentAndProofOfKnowledgeOnKey, CommitmentAndProofOfKnowledge.class);
                // Send commitment on key and index to the room
                node.getSender().send(commitmentAndProofOfKnowledgeOnKeyJson);

                /** RECEIVE COMMITMENTS AND POKs ON KEYS **/
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
                    if (!zkp.verifyProofOfKnowledgePedersen(receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge(), receivedCommitmentOnKey))
                        System.out.println("WRONG PoK on Key. Round: " + round + ", Node: " + receivedCommitmentAndProofOfKnowledgeOnKey.getProofOfKnowledge().getNodeIndex());
                    // Calculate multiplication of incoming commitments
                    multiplicationOnCommitments = multiplicationOnCommitments.multiply(receivedCommitmentOnKey).mod(room.getP());
                }
                // Check that multiplication result is 1
                if (!multiplicationOnCommitments.equals(BigInteger.ONE))
                    System.out.println("Round " + round + " commitments on keys are WRONG");

                // Synchronize nodes to let know that we all finish the key commitments part
                synchronizeNodes(nodeIndex, repliers, requestors, room);

                /** SEND COMMITMENT AND POK ON MESSAGE **/
                // Set protocol message to make a commitment to
                BigInteger protocolMessage;
                if (!messageInThisRound)
                    protocolMessage = BigInteger.ZERO;
                else
                    protocolMessage = outputParticipantMessage.getProtocolMessage();
                // Generate random value for commitment
                BigInteger randomForCommitmentOnMessage = pedersenCommitment.generateRandom();
                // Generate Commitment on Message
                BigInteger commitmentOnMessage = pedersenCommitment.calculateCommitment(protocolMessage, randomForCommitmentOnMessage);
                // Generate ProofOfKnowledgePedersen associated with the commitment for the protocol message, using randomForCommitment as the necessary random value
                ProofOfKnowledgePedersen proofOfKnowledgeOnMessage = zkp.generateProofOfKnowledgePedersen(protocolMessage, randomForCommitmentOnMessage);
                // Generate JSON string of an Object containing both commitment and proofOfKnowledge
                CommitmentAndProofOfKnowledge commitmentAndProofOfKnowledgeOnMessage = new CommitmentAndProofOfKnowledge(commitmentOnMessage, proofOfKnowledgeOnMessage);
                String commitmentAndProofOfKnowledgeOnMessageJson = new Gson().toJson(commitmentAndProofOfKnowledgeOnMessage, CommitmentAndProofOfKnowledge.class);
                // Send Json to the room (which contains the commitment and the proofOfKnowledge)
                node.getSender().send(commitmentAndProofOfKnowledgeOnMessageJson);

                /** RECEIVE COMMITMENTS AND POKs ON MESSAGES **/
                // Receive proofs of other participant nodes where we need to check each of them
                for (int i = 0; i < room.getRoomSize(); i++) {
                    // Wait response from Receiver thread as a String (json)
                    String receivedCommitmentAndProofOfKnowledgeOnMessageJson = receiverThread.recvStr();
                    // Transform String (json) to object ProofOfKnowledgePedersen
                    CommitmentAndProofOfKnowledge receivedCommitmentAndProofOfKnowledgeOnMessage = new Gson().fromJson(receivedCommitmentAndProofOfKnowledgeOnMessageJson, CommitmentAndProofOfKnowledge.class);
                    // Store commitment for future checking
                    commitmentsOnMessage[receivedCommitmentAndProofOfKnowledgeOnMessage.getProofOfKnowledge().getNodeIndex() - 1] = receivedCommitmentAndProofOfKnowledgeOnMessage.getCommitment();
                    // Verify proof of knowledge
                    if (!zkp.verifyProofOfKnowledgePedersen(receivedCommitmentAndProofOfKnowledgeOnMessage.getProofOfKnowledge(), receivedCommitmentAndProofOfKnowledgeOnMessage.getCommitment()))
                        System.out.println("WRONG PoK. Round: " + round + ", Node: " + receivedCommitmentAndProofOfKnowledgeOnMessage.getProofOfKnowledge().getNodeIndex());
                }

                // Synchronize nodes to let know that we all finish the commitments on messages part
                synchronizeNodes(nodeIndex, repliers, requestors, room);

                /** SEND OUTPUT MESSAGE AND POK ASSOCIATED **/
                // Add round key to the message
                outputParticipantMessage.setRoundKeyValue(keyRoundValue);
                zeroMessage.setRoundKeyValue(keyRoundValue);
                // Set Proof of Knowledge that is needed for round 1
                if (round == 1) {
                    // Calculate random for commitment as the sum of both random used before (commitment on key and commitment on message)
                    BigInteger randomForCommitmentOnOutputMessage = randomRoundValue.add(randomForCommitmentOnMessage);
                    // Generate proofOfKnowledge for OutputMessage, as a commitment for the sum of both randomness used (for commitments on key and message)
                    ProofOfKnowledge proofOfKnowledgeOnOutputMessage = zkp.generateProofOfKnowledge(randomForCommitmentOnOutputMessage);
                    // Generate Json string with Object containing both outputMessage and proofOfKnowledge
                    OutputMessageAndProofOfKnowledge outputMessageAndProofOfKnowledge = new OutputMessageAndProofOfKnowledge(outputParticipantMessage, proofOfKnowledgeOnOutputMessage);
                    String outputMessageAndProofOfKnowledgeJson = new Gson().toJson(outputMessageAndProofOfKnowledge, OutputMessageAndProofOfKnowledge.class);
                    // Send the Json to the room (which contains the outputMessage and the proofOfKnowledge)
                    node.getSender().send(outputMessageAndProofOfKnowledgeJson);
                }
                else {
                    // Create Json objects with each possible message to send
                    outputParticipantMessageJson = new Gson().toJson(outputParticipantMessage);
                    zeroMessageJson = new Gson().toJson(zeroMessage);
                    // Set the corresponding message to send in this round
                    String outputMessageRoundJson;
                    if (messageInThisRound)
                        outputMessageRoundJson = outputParticipantMessageJson;
                    else
                        outputMessageRoundJson = zeroMessageJson;
                    // Send the message
                    node.getSender().send(outputMessageRoundJson);
                }

                /** RECEIVE OUTPUT MESSAGES AND POKs ASSOCIATED **/
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
                        if (!zkp.verifyProofOfKnowledge(outputMessageAndProofOfKnowledge.getProofOfKnowledge(), beta))
                            System.out.println("WRONG PoK on OutputMessage. Round: " + round + ", Node: " + participantNodeIndex);
                        // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(outputMessageAndProofOfKnowledge.getOutputMessage().getProtocolMessage()).mod(room.getP());
                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                }
                else {
                    // Variable to count how many messages were received from the receiver thread
                    int messagesReceivedInThisRound = 0;
                    // When this number equals the total number of participants nodes in the room, it means that i've received all the messages in this round
                    while (messagesReceivedInThisRound < room.getRoomSize()) {
                        // Receive a message
                        byte[] messageReceivedFromReceiverThread = receiverThread.recv();
                        // Transform incoming message to a BigInteger
                        BigInteger incomingOutputMessage = new BigInteger(messageReceivedFromReceiverThread);
                        // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                        sumOfO = sumOfO.add(incomingOutputMessage).mod(room.getP());
                        // Increase the number of messages received
                        messagesReceivedInThisRound++;
                    }
                }
            }

            /** VIRTUAL ROUND (odd rounds) **/
            else {
                // Recover messages sent in rounds (2*round) and round in order to construct the resulting message of this round (see Reference for more information)
                BigInteger sumOfOSentInRound2K = messagesSentInPreviousRounds.get(round - 1);
                BigInteger sumOfOSentInRoundK = messagesSentInPreviousRounds.get((round - 1) / 2);
                // Construct the resulting message of this round
                sumOfO = sumOfOSentInRoundK.subtract(sumOfOSentInRound2K);
            }

            /** EXTRACT ROUND MESSAGES VALUES **/
            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, sumOfO);
            // Divide sumOfO in sumOfM and sumOfT (see Reference for more information)
            sumOfM = sumOfO.divide(BigInteger.valueOf(room.getRoomSize() + 1));
            sumOfT = sumOfO.subtract(sumOfM.multiply(BigInteger.valueOf(room.getRoomSize() + 1)));

            // Print info about the messages sent in probabilistic mode
            if (!room.getNonProbabilisticMode()) {
                if (sumOfM.toString().length() > 15)
                    System.out.println("C_" + round + ":\t(" + sumOfM.toString().substring(0, 10) + "..., " + sumOfT + ")");
                else
                    System.out.println("C_" + round + ":\t(" + sumOfM + ", " + sumOfT + ")");
            }

            // If we are playing the first round, assign the size of the collision
            if (round == 1) {
                collisionSize = Integer.parseInt(sumOfT.toString());
                // If the size is 0, it means that no messages were sent during this session, so we finish the protocol
                if (collisionSize == 0) {
                    System.out.println("NO MESSAGES WERE SENT");
                    finished = true;
                    continue;
                }
            }

            /** NO COLLISION ROUND **/
            // <sumOfT> = 1 => No Collision Round => a message went through clearly, received by the rest of the nodes
            if (sumOfT.equals(BigInteger.ONE)) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                if (messagesSentWithNoCollisions == 1)
                    firstMessageTime = System.nanoTime() - t1;

                // Print message that went through the protocol
                // out.println("ANON:\t" + OutputMessage.getMessageWithoutRandomness(sumOfM, room));
                String singleMessage = OutputMessage.getMessageWithoutRandomness(sumOfM, room);
                out.println(singleMessage);

                /* If the message that went through is mine, my message was transmitted.
                 * We have to set the variable in order to start sending zero messages in subsequently rounds */
                if (outputParticipantMessage.getParticipantMessageWithPaddingBigInteger().equals(sumOfM))
                    messageTransmitted = true;

                /* If the number of messages that went through equals the collision size, the first collision was completely resolved.
                 * Set variable to finalize the protocol in the next round */
                if (messagesSentWithNoCollisions == collisionSize)
                    finished = true;
            }

            else {
                /** PROBLEMATIC ROUND **/
                // <sumOfT> == 0 => if we are in a deterministic mode, this means that someone cheated, and it's necessary to change the mode
                if (sumOfT.equals(BigInteger.ZERO)) {
                    // Change resending mode
                    if (room.getNonProbabilisticMode()) {
                        System.out.println("ROUND " + round + " PROBLEMATIC: CHANGING RESENDING MODE TO PROBABILISTIC"); // *******
                        room.setNonProbabilisticMode(false);
                    }
                }

                /** COLLISION ROUND **/
                // <sumOfT> > 1 => A Collision was produced
                if (sumOfT.compareTo(BigInteger.ONE) > 0) {

                    /** PROBLEMATIC ROUND **/
                    // <sumOfT> repeats in this real round and the "father" round. Someone cheated and it's necessary to change the mode
                    if (round != 1 && round%2 == 0 && sumOfO.equals(messagesSentInPreviousRounds.get(round/2))) {
                        // Remove next round to happen (it will be a virtual round with no messages sent)
                        removeRoundToHappen(nextRoundsToHappen, round + 1);
                        // Change resending mode
                        if (room.getNonProbabilisticMode()) {
                            System.out.println("ROUND " + round + " PROBLEMATIC: CHANGING RESENDING MODE TO PROBABILISTIC"); // *******
                            room.setNonProbabilisticMode(false);
                        }
                    }

                    /** RESENDING PROTOCOL **/
                    // Check if my message was involved in the collision, checking if in this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        // Non probabilistic mode (see Reference for more information)
                        if (room.getNonProbabilisticMode()) {
                            // Calculate average message, if my message is below that value i re-send in the round (2*round)
                            if (outputParticipantMessage.getParticipantMessageWithPaddingBigInteger().compareTo(sumOfM.divide(sumOfT)) <= 0) {
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
                    addRoundsToHappenNext(nextRoundsToHappen, 2 * round, 2 * round + 1);
                }
            }
        }

        // Finish time measurement
        long t2 = System.nanoTime();
        // Save execution time
        executionTime = t2-t1;
        // Save average time per message
        averageTimePerMessage = executionTime / messagesSentWithNoCollisions;
    }

    /**
     *
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
     *
     * @param commitmentsOnKeys commitments in each individual round key (shared with another participant node)
     * @param room Room where the participant node is playing
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
     *
     * @return total execution time of this session
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     *
     * @return time to get the first message of this session
     */
    public long getFirstMessageTime() {
        return firstMessageTime;
    }

    /**
     *
     * @return average time per message of this session
     */
    public long getAverageTimePerMessage() {
        return averageTimePerMessage;
    }

    /**
     *
     * @param nodeIndex index of the participant node
     * @param repliers array with zmq sockets that work as repliers
     * @param requestors array with zmq sockets that work as requestors
     * @param room room where the messages are going send
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
     * @param nextRoundsToHappen list with rounds that are going to happen in the future
     * @param round index of the round that wants to remove from happening
     */
    private void removeRoundToHappen(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.removeFirstOccurrence(round);
    }

    /**
     * Add a round to happen immediately after the running one
     * @param nextRoundsToHappen list with rounds that are going to happen in the future
     * @param round index of the round that wants to add to happen in the future
     */
    private void addRoundToHappenFirst(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.addFirst(round);
    }

    /**
     * Add two rounds to happen afterwards (they are added at the end of the LinkedList)
     * @param nextRoundsToHappen list with rounds that are going to happen in the future
     * @param firstRoundToAdd index of the round
     * @param secondRoundToAdd index of the round
     */
    private void addRoundsToHappenNext(LinkedList<Integer> nextRoundsToHappen, int firstRoundToAdd, int secondRoundToAdd) {
        nextRoundsToHappen.add(firstRoundToAdd);
        nextRoundsToHappen.add(secondRoundToAdd);
    }

    /**
     * Add one round to happen afterwards (is added at the end of the LinkedList)
     * @param nextRoundsToHappen list with round that are going to happen in the future
     * @param round index of the round to add
     */
    private void addRoundToHappenNext(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.add(round);
    }

    /**
     * Create all the repliers (the quantity depends on the index of the node) socket
     * necessary to run the protocol (see Reference for more information)
     * @param nodeIndex index of the participant node
     * @param context context where the zmq sockets are going to run
     */
    public void initializeRepliersArray(int nodeIndex, ZContext context) {
        // Create an array of sockets
        ZMQ.Socket[] repliers = null;
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1) {
            // Initialize the array with exactly (<nodeIndex> - 1) sockets
            repliers = new ZMQ.Socket[nodeIndex-1];
            for (int i = 0; i < repliers.length; i++) {
                // Create the REP socket
                repliers[i] = context.createSocket(ZMQ.REP);
                // Bind this REP socket to the correspondent port in order to be connected by his correspondent REQ socket of another node
                repliers[i].bind("tcp://*:" + (7000+i));
            }
        }
        // Return the array with the replier sockets
        this.repliers = repliers;
    }

    /**
     * Create all the requestors (the quantity depends on the index of the node) socket necessary to run the protocol
     * (see Reference for more information)
     * @param nodeIndex index of the participant node
     * @param context context where the zmq sockets are going to run
     * @param room room where the messages are being sent
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
     *
     * @return number of real rounds played in this session
     */
    public int getRealRoundsPlayed() {
        return realRoundsPlayed;
    }

    /**
     *
     * @param nodeIndex index of the participant node
     * @param roomSize size of the room
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
