package participantnode;

import com.google.gson.Gson;
import crypto.PedersenCommitment;
import dcnet.Room;
import keygeneration.DiffieHellman;
import keygeneration.KeyGeneration;
import keygeneration.SecretSharing;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

/**
 *
 */
public class SessionManager {

    private ZMQ.Socket[] repliers,
                         requestors;
    private boolean messageTransmitted,
                    finished;
    //        realRound;
    private int round,
                realRoundsPlayed,
                nextRoundAllowedToSend,
                collisionSize,
                messagesSentWithNoCollisions;
    private Dictionary<Integer, BigInteger> messagesSentInPreviousRounds;
    private LinkedList<Integer> nextRoundsToHappen;
    // private List<BigInteger> messagesReceived;
    private PedersenCommitment pedersenCommitment;
    private BigInteger commitment;

    private long executionTime;

    /**
     *
     */
    public SessionManager() {
        // realRound = true;
        messageTransmitted = false;
        round = 1;
        realRoundsPlayed = 0;
        nextRoundAllowedToSend = 1;
        collisionSize = 0;
        messagesSentInPreviousRounds = new Hashtable<>();
        messagesSentWithNoCollisions = 0;
        finished = false;
        nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);
        // messagesReceived = new LinkedList<>();
        executionTime = 0;
        pedersenCommitment = new PedersenCommitment();
        commitment = BigInteger.ZERO;
    }

    /**
     *
     * @param nodeIndex index of the participant node
     * @param participantMessage string with the message that participant node wants to communicate
     * @param room room where the message is going to be send
     * @param node participant node
     * @param receiverThread thread where participant node is listening
     */
    public void runSession(int nodeIndex, String participantMessage, Room room, ParticipantNode node, ZMQ.Socket receiverThread, PrintStream out) throws UnsupportedEncodingException {

        // Print info about the room
        System.out.println("Number of nodes: " + room.getRoomSize());
        System.out.println("My index is: " + nodeIndex);

        // Create an outputMessage and a zeroMessage participantnode.OutputMessage objects
        OutputMessage outputParticipantMessage = new OutputMessage();
        outputParticipantMessage.setPaddingLength(room.getPadLength());
        outputParticipantMessage.setParticipantMessage(participantMessage, room);
        String outputParticipantMessageJson = new Gson().toJson(outputParticipantMessage);

        OutputMessage zeroMessage = new OutputMessage();
        zeroMessage.setParticipantMessage("0", room);
        String zeroMessageJson = new Gson().toJson(zeroMessage);

        boolean messageInThisRound;

        // Print message to send in this session
        System.out.println("\nm_" + nodeIndex + " = " + participantMessage + "\n");

        // Sleep to overlap slow joiner problem
        // TODO: fix this using a better solution
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        pedersenCommitment = new PedersenCommitment(room.getG(), room.getH(), room.getQ(), room.getP());

        long t1 = System.nanoTime();

        // Each loop of this while is a different round
        while (!Thread.currentThread().isInterrupted()) {

            // Synchronize nodes at the beginning of each round
            synchronizeNodes(nodeIndex, repliers, requestors, room);

            // Check if the protocol was finished in the last round played
            // If it so, let know to the receiver thread, wait for his response and break the loop
            if (finished) {
                receiverThread.send("FINISHED");
                receiverThread.recvStr();
                break;
            }
            // If not is finished yet, get which round we need to play and send this round to the receiver thread
            else {
                round = nextRoundsToHappen.removeFirst();
                receiverThread.send("" + round);
            }

            // Variables to store the resulting message of the round
            BigInteger sumOfM, sumOfT, sumOfO = BigInteger.ZERO;

            // The protocol separates his operation if it's being played a real round or a virtual one (see Reference for more information)
            // REAL ROUND (first and even rounds)
            if (round == 1 || round%2 == 0) {

                messageInThisRound = !messageTransmitted && nextRoundAllowedToSend == round;

                // Set variable that we are playing a real round and add one to the count
                realRoundsPlayed++;

                // KEY SHARING PART
                KeyGeneration keyGeneration = new SecretSharing(room.getRoomSize() - 1, nodeIndex, repliers, requestors, room);
                // KeyGeneration keyGeneration = new DiffieHellman(room.getRoomSize() - 1, room.getG(), room.getP(), nodeIndex, repliers, requestors, room);
                keyGeneration.generateParticipantNodeValues();
                keyGeneration.getOtherParticipantNodesValues();

                BigInteger keyRoundValue = keyGeneration.getParticipantNodeRoundKeyValue();

                // Synchronize nodes to let know that we all finish the Key-Sharing part
                synchronizeNodes(nodeIndex, repliers, requestors, room);

                // COMMITMENTS ON KEYS PART
                BigInteger[] roundKeys = keyGeneration.getRoundKeys();
                BigInteger[] commitmentsOnKeys = new BigInteger[roundKeys.length];
                for (int i = 0; i < roundKeys.length; i++) {
                    commitmentsOnKeys[i] = pedersenCommitment.calculateCommitment(roundKeys[i]);
                }
                BigInteger commitmentOnKey = generateCommitmentOnKey(commitmentsOnKeys, room);

                // Send commitment on key to the room
                node.getSender().send(commitmentOnKey.toString());

                // Wait response from Receiver thread
                receiverThread.recvStr();
                // TODO: Do something with the commitments

                // Synchronize nodes to let know that we all finish the key commitments part
                synchronizeNodes(nodeIndex, repliers, requestors, room);

                // SET MESSAGE OF THIS ROUND
                // We have two possibilities: or send a zero message or a different one
                // If my message was already sent in a round with no collisions, i set a zero message
                /*String outputMessageRoundJson;
                if (messageInThisRound)
                    outputMessageRoundJson = outputParticipantMessageJson;
                else
                    outputMessageRoundJson = zeroMessageJson;*/

                /*if (messageTransmitted) {
                    outputMessageRoundJson = zeroMessageJson;
                }
                // If not, check first if i'm allowed to send my message in this round
                // If so i set my message as outputMessage set before
                else if (nextRoundAllowedToSend == round) {
                    outputMessageRoundJson = outputParticipantMessageJson;
                }
                // If not, i set a zero message
                else {
                    outputMessageRoundJson = zeroMessageJson;
                }*/

                // COMMITMENT ON MESSAGE PART
                // Calculate commitment on message
                if (!messageInThisRound)
                    commitment = pedersenCommitment.calculateCommitment(BigInteger.ZERO);
                else
                    commitment = pedersenCommitment.calculateCommitment(outputParticipantMessage.getProtocolMessage());

                // Send commitment to the room
                node.getSender().send(commitment.toString());

                // Wait response from participantnode.Receiver thread
                receiverThread.recvStr();
                // TODO: Do something with the commitments

                // Synchronize nodes to let know that we all finish the commitments on messages part
                synchronizeNodes(nodeIndex, repliers, requestors, room);

                // Add round key to the message
                outputParticipantMessage.setRoundKeyValue(keyRoundValue);
                outputParticipantMessageJson = new Gson().toJson(outputParticipantMessage);
                //zeroMessage.setRoundKeyValue(keyRoundValue);
                //zeroMessageJson = new Gson().toJson(zeroMessage);

                // If my message was already sent in a round with no collisions, i set a zero message
                String outputMessageRoundJson;
                if (messageInThisRound) {
                    //outputParticipantMessage.setRoundKeyValue(keyRoundValue);
                    //outputParticipantMessageJson = new Gson().toJson(outputParticipantMessage);
                    outputMessageRoundJson = outputParticipantMessageJson;
                }
                else {
                    zeroMessage.setRoundKeyValue(keyRoundValue);
                    zeroMessageJson = new Gson().toJson(zeroMessage);
                    outputMessageRoundJson = zeroMessageJson;
                }

                // MESSAGE SENDING
                // Send the message
                node.getSender().send(outputMessageRoundJson);

                // RECEIVE MESSAGES FROM OTHER NODES
                // After sending my message, receive information from the receiver thread (all the messages sent in this round by all the nodes in the room)
                // Count how many messages were receive from the receiver thread
                int messagesReceivedInThisRound = 0;
                // When this number equals <dcNetSize> i've received all the messages in this round
                while (messagesReceivedInThisRound < room.getRoomSize()) {
                    // Receive a message
                    byte[] messageReceivedFromReceiverThread = receiverThread.recv();
                    // Transform incoming message to an int
                    BigInteger incomingOutputMessage = new BigInteger(messageReceivedFromReceiverThread);
                    // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                    sumOfO = sumOfO.add(incomingOutputMessage);
                    // Increase the number of messages received
                    messagesReceivedInThisRound++;
                }

            }

            // VIRTUAL ROUND (odd rounds)
            else {
                // Recover messages sent in rounds 2k and k in order to construct the resulting message of this round (see Reference for more information)
                BigInteger sumOfOSentInRound2K = messagesSentInPreviousRounds.get(round - 1);
                BigInteger sumOfOSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                // Construct the resulting message of this round
                sumOfO = sumOfOSentInRoundK.subtract(sumOfOSentInRound2K);
            }

            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, sumOfO);

            // Divide sumOfO in sumOfM and sumOfT (see Reference for more information)
            sumOfM = sumOfO.divide(BigInteger.valueOf(room.getRoomSize() + 1));
            sumOfT = sumOfO.subtract(sumOfM.multiply(BigInteger.valueOf(room.getRoomSize() + 1)));

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

            // Depending on the resulting message, we have to analyze either there was a collision or not in this round
            // <sumOfT> = 1 => No Collision Round => a message went through clearly, received by the rest of the nodes
            if (sumOfT.equals(BigInteger.ONE)) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // Print message that went through the protocol
                out.println("ANON: " + OutputMessage.getMessageWithoutRandomness(sumOfM));

                // If the message that went through is mine, my message was transmitted
                // We have to set the variable in order to start sending zero messages in subsequently rounds
                if (outputParticipantMessage.getParticipantMessageWithPaddingBigInteger().equals(sumOfM))
                    messageTransmitted = true;

                // If the number of messages that went through equals the collision size, the collision was completely resolved
                // Set variable to finalize the protocol in the next round
                if (messagesSentWithNoCollisions == collisionSize)
                    finished = true;
            }

            // <sumOfT> != 1 => Collision produced or no messages sent in this round (last can only occur in probabilistic mode)
            else {
                // In probabilistic mode, two things could happen and they are both solved the same way: (see Reference for more information)
                // 1) No messages were sent in a real round (<sumOfT> = 0)
                // 2) All messages involved in the collision of the "father" round are sent in this round and the same collision is produced
                // if (round != 1 && (sumOfT == 0 || sumOfO == messagesSentInPreviousRounds.get(round/2))) {
                if (round != 1 && (sumOfT.equals(BigInteger.ZERO) || sumOfO.equals(messagesSentInPreviousRounds.get(round/2)))) {
                    // We have to re-do the "father" round in order to expect that no all nodes involved in the collision re-send their message in the same round
                    // Add the "father" round to happen after this one
                    addRoundToHappenFirst(nextRoundsToHappen, round/2);
                    // Remove the virtual round related to this problematic round
                    removeRoundToHappen(nextRoundsToHappen, round+1);
                    // As we removed the next round from happening, we have to reassign the sending round to the "father" round once more
                    if (nextRoundAllowedToSend == round+1 || nextRoundAllowedToSend == round)
                        nextRoundAllowedToSend = round/2;
                }
                // In either re-sending modes, a "normal" collision can be produced
                // <sumOfT> > 1 => A Collision was produced
                else {
                    // Check if my message was involved in the collision, checking if in this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        // Check in which mode of re-sending my message we are
                        // Non probabilistic mode (see Reference for more information)
                        if (room.getNonProbabilisticMode()) {
                            // Calculate average message, if my message is below that value i re-send in the round (2*round)
                            if (outputParticipantMessage.getParticipantMessageWithPaddingBigInteger().compareTo(sumOfM.divide(sumOfT)) <= 0)
                                nextRoundAllowedToSend = 2 * round;
                            // If not, i re-send my message in the round (2*round + 1)
                            else {
                                nextRoundAllowedToSend = 2 * round + 1;
                            }
                        }
                        // Probabilistic mode (see Reference for more information)
                        else {
                            // Throw a coin to see if a send in the round (2*round) or (2*round + 1)
                            boolean coin = new SecureRandom().nextBoolean();
                            if (coin) {
                                nextRoundAllowedToSend = 2 * round;
                            } else {
                                nextRoundAllowedToSend = 2 * round + 1;
                            }
                        }
                    }
                    // Add (2*round) and (2*round + 1) rounds to future plays
                    addRoundsToHappenNext(nextRoundsToHappen, 2 * round, 2 * round + 1);
                }
            }
        }

        long t2 = System.nanoTime();

        executionTime = t2-t1;

    }

    private BigInteger generateCommitmentOnKey(BigInteger[] commitmentsOnKeys, Room room) {
        BigInteger _a = BigInteger.ONE;
        for (BigInteger commitmentsOnKey : commitmentsOnKeys) {
            _a = _a.multiply(commitmentsOnKey);
        }
        return _a.mod(room.getP());
    }

    private BigInteger[] sendAndReceiveCommitmentsOnKeys(BigInteger[] commitments, int nodeIndex, Room room) {
        int i = 0;
        BigInteger[] otherNodesKeyCommitments = new BigInteger[commitments.length];
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                otherNodesKeyCommitments[i] = new BigInteger(replier.recvStr());
                // When the replier receives the message, replies with one of their key shares
                replier.send(commitments[i].toString());
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(commitments[i].toString());
                // The requestor waits to receive a reply with one of the key shares
                otherNodesKeyCommitments[i] = new BigInteger(requestor.recvStr());
                i++;
            }
        return otherNodesKeyCommitments;
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
