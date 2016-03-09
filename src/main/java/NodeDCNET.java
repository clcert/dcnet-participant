import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.*;

/*
    This application runs a collision resolution protocol, in order
    to use within a DC-NET.
    The nodes that are created by this class are run in computers
    connected through a LAN.
    Also is necessary to run within the same LAN a Directory Node,
    in order to inform the IP address of the rest of the room.
*/

class NodeDCNET implements ZThread.IAttachedRunnable {

    private int dcNetSize;
    private final String myIp;
    private final String name;
    private final int message;
    private int nodeIndex;
    private final String directoryIp;
    private final boolean nonProbabilistic;
    private static HashMap<Integer, String> directory = new HashMap<>();

    public NodeDCNET(String myIp, String name, String message, String directoryIp, String nonProbabilistic) {
        this.myIp = myIp;
        this.name = name;
        this.message = Integer.parseInt(message);
        this.directoryIp = directoryIp;
        this.nonProbabilistic = Boolean.parseBoolean(nonProbabilistic);
    }

    // Usage: ./gradlew run -PappArgs=[<message>,<directoryIP>,<probabilisticMode>]
    public static void main(String[] args) throws IOException {
        String myIp = getLocalNetworkIp();
        System.out.println("my IP: " + myIp + "\n");
        new NodeDCNET(myIp, "Node", args[0], args[1], args[2]).createNode();
    }

    // Receiver Thread
    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {

        // Create the receiver socket that work as a subscriber
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Set size of the DC-NET room
        int dcNetRoomSize = (int) args[0];

        // Connect as a subscriber to each of the nodes on the DC-NET room
        connectReceiverThread(receiver, dcNetRoomSize);

        // Subscribe to whatever the nodes say
        receiver.subscribe("".getBytes());

        // Read from other nodes while is not being interrupted
        while (!Thread.currentThread().isInterrupted()) {

            // Receive message from the sender thread
            String inputFromSender = pipe.recvStr();

            // Check if the message is a Finished signal
            if (inputFromSender.equals("FINISHED"))
                break;

            // If not is finished, it is the number of the round that the room is playing
            int round = Integer.parseInt(inputFromSender);

            // If the round is virtual, the receiver thread will not receive any message from the room, so we skip it
            if (round != 1 && round%2 != 0) {
                continue;
            }

            // We are in a real round, so we iterate in order to receive all the messages from the other nodes
            for (int i = 0; i < dcNetRoomSize; i++) {
                // Receive message from a node in the room
                String inputMessage = receiver.recvStr().trim();

                // Format the message that is incoming to "extract" the actual message
                OutputMessage incomingOutputMessage = new Gson().fromJson(inputMessage, OutputMessage.class);
                int numericInputMessage = incomingOutputMessage.getMessage();

                // Send to the sender thread the message received
                pipe.send("" + numericInputMessage);
            }

        }

        // Close receiver thread
        receiver.close();

        // Let know to the sender that i'm already closed
        pipe.send("");

    }

    // Connect Receiver thread to the rest of the nodes publishers sockets
    private void connectReceiverThread(ZMQ.Socket receiver, int dcNetSize) {
        for (int i = 1; i <= dcNetSize; i++)
            receiver.connect("tcp://" + directory.get(i) + ":9000");
    }

    // Sender Thread and Collision Resolution Protocol
    public void createNode() throws IOException {

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Create the sender socket that works as a publisher
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);

        // Bind sender port (by default is in port 9000 when working on a LAN)
        bindSenderPort(sender);

        // Create Directory Subscriber and connect to 5555 port
        ZMQ.Socket directorySubscriber = context.createSocket(ZMQ.SUB);
        directorySubscriber.connect("tcp://" + directoryIp + ":5555");
        directorySubscriber.subscribe("".getBytes());

        // Create Directory Push and connect to 5554 port
        ZMQ.Socket directoryPush = context.createSocket(ZMQ.PUSH);
        directoryPush.connect("tcp://" + directoryIp + ":5554");

        // Send my IP to the Directory through the PUSH socket
        directoryPush.send(myIp);

        // Wait message from the Directory node (using the SUB socket) with all the {index,ip} pairs of the room
        String directoryJson = directorySubscriber.recvStr();

        // Create object Directory serializing the Json message received from the directory node
        Directory directory = new Gson().fromJson(directoryJson, Directory.class);
        for (int i = 0; i < directory.nodes.length; i++) {
            NodeDCNET.directory.put(directory.nodes[i].index, directory.nodes[i].ip);
        }

        // Rescue index (key) of this node given my ip (value)
        Set directorySet = NodeDCNET.directory.entrySet();
        for (Object aDirectorySet : directorySet) {
            Map.Entry mapEntry = (Map.Entry) aDirectorySet;
            int indexKey = (int) mapEntry.getKey();
            String ipValue = NodeDCNET.directory.get(indexKey);
            if (ipValue.equals(myIp))
                nodeIndex = indexKey;
        }

        // Set number of nodes in the room
        dcNetSize = directory.nodes.length;

        // Print info about the room
        System.out.println("Number of nodes: " + dcNetSize);
        System.out.println("My index is: " + nodeIndex);

        // We need to connect every pair of nodes in order to synchronize the sending of values at the beginning of each round
        // For this, we need that in every pair of nodes there will be one requestor and one replier
        // In every pair of nodes {i,j} where i<j, node i will work as a requestor and node j will work as a replier
        // Create array of sockets that will work as repliers and requestors
        ZMQ.Socket[] repliers = initializeRepliersArray(nodeIndex, context);
        ZMQ.Socket[] requestors = initializeRequestorsArray(nodeIndex, context);

        // Throw receiver thread which runs the method 'run' described above
        ZMQ.Socket receiverThread = ZThread.fork(context, new NodeDCNET(this.myIp, this.name, "" + this.message, this.directoryIp, "" + this.nonProbabilistic), dcNetSize);

        // Sleep to overlap slow joiner problem
        // TODO: fix this using a better solution
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create OutputMessage object
        OutputMessage outputMessage = new OutputMessage();
        outputMessage.setSenderId("Node_" + nodeIndex);
        outputMessage.setCmd(1);

        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        int message = this.message;
        // If the message is 0, the node doesn't want to send any message to the room
        if (message == 0) {
            outputMessage.setMessage(0);
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            outputMessage.setMessage(message*(dcNetSize+1) + 1);
        }
        // Create Json with the message that the node wants to communicate to the rest of the room
        String outputMessageJson = new Gson().toJson(outputMessage);

        // Print message to send
        System.out.println("\nm_" + nodeIndex + " = " + message + "\n");

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Index to know in what round we are and if this round is either real or virtual
        int round;
        boolean realRound;

        // Index to know in which round i'm allowed to resend my message
        int nextRoundAllowedToSend = 1;

        // How many messages are being involved in the first collision
        int collisionSize = 0;

        // Store messages sent in previous rounds in order to build the message in the virtual rounds
        Dictionary<Integer, Integer> messagesSentInPreviousRounds = new Hashtable<>();

        // Count how many messages were sent without collisions
        // When this number equals the collision size, the first collision was resolved and the protocol over
        int messagesSentWithNoCollisions = 0;

        // Variable to see if the first collision was solved or not
        boolean finished = false;

        // Variable to store the next rounds that are allow to happen (this is use to skip rounds that no node will send a value)
        LinkedList<Integer> nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);

        // Create the zeroMessage which is used several times on the protocol (either when is not my turn to send or i already sent my message)
        String zeroMessageJson = new Gson().toJson(new OutputMessage("Node_" + nodeIndex, 1, 0));

        // Store all the messages received to show them at the end of the protocol
        List<Integer> messagesReceived = new LinkedList<>();

        // Measure execution time (real time)
        long t1 = 0;

        // Begin the collision resolution protocol
        // Every loop is a new round that is being played
        while (!Thread.currentThread().isInterrupted()) {

            // Synchronize nodes at the beginning of each round
            synchronizeNodes(nodeIndex, repliers, requestors);

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

            // If it is the first round, "start the clock" in order to measure total time of execution (real time)
            if (round == 1)
                t1 = System.nanoTime();

            // Print round number
            System.out.println("ROUND " + round);

            // Variables to store the resulting message of the round
            int sumOfM, sumOfT, sumOfO = 0;

            // The protocol separates his operation if it's being played a real round or a virtual one (see Reference for more information)
            // REAL ROUND (first and even rounds)
            if (round == 1 || round%2 == 0) {
                // Set variable that we are playing a real round and print it
                realRound = true;
                System.out.println("REAL ROUND");

                // If my message was already sent in a round with no collisions, i send a zero message
                if (messageTransmitted) {
                    sender.send(zeroMessageJson);
                }

                // If not, check first if i'm allowed to send my message in this round
                // If so i send my message as the Json string constructed before the round began
                else if (nextRoundAllowedToSend == round) {
                    sender.send(outputMessageJson);
                }
                // If not, i send a zero message
                else {
                    sender.send(zeroMessageJson);
                }

                // After sending my message, receive information from the receiver thread (all the messages sent in this round by all the nodes in the room)
                // Count how many messages were receive from the receiver thread
                int messagesReceivedInThisRound = 0;
                // When this number equals <dcNetSize> i've received all the messages in this round
                while (messagesReceivedInThisRound < dcNetSize) {
                    // Receive a message
                    String messageReceivedFromReceiverThread = receiverThread.recvStr();
                    // Transform incoming message to an int
                    int incomingOutputMessage = Integer.parseInt(messageReceivedFromReceiverThread);
                    // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                    sumOfO += incomingOutputMessage;
                    // Increase the number of messages received
                    messagesReceivedInThisRound++;
                }

            }

            // VIRTUAL ROUND (odd rounds)
            else {
                // Set variable that we are playing a virtual round and print it
                realRound = false;
                System.out.println("VIRTUAL ROUND");

                // Recover messages sent in rounds 2k and k in order to construct the resulting message of this round (see Reference for more information)
                int sumOfOSentInRound2K = messagesSentInPreviousRounds.get(round - 1);
                int sumOfOSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                // Construct the resulting message of this round
                sumOfO = sumOfOSentInRoundK - sumOfOSentInRound2K;
            }

            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, sumOfO);

            // Divide sumOfO in sumOfM and sumOfT (see Reference for more information)
            sumOfM = sumOfO/(dcNetSize + 1);
            sumOfT = sumOfO - (sumOfM*(dcNetSize + 1));

            // Print resulting message of this round
            System.out.println("C_" + round +  " = (" + sumOfM + "," + sumOfT + ")");

            // If we are playing the first round, assign the size of the collision
            if (round == 1) {
                collisionSize = sumOfT;
                // If the size is 0, it means that no messages were sent during this session, so we finish the protocol
                if (collisionSize == 0) {
                    System.out.println("NO MESSAGES WERE SENT");
                    finished = true;
                    continue;
                }
            }

            // Depending on the resulting message, we have to analyze either there was a collision or not in this round
            // <sumOfT> = 1 => No Collision Round => a message went through clearly, received by the rest of the nodes
            if (sumOfT == 1) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // Add message received in this round in order to calculate messages in subsequently virtual rounds
                messagesReceived.add(sumOfM);

                // If the message that went through is mine, my message was transmitted
                // We have to set the variable in order to start sending zero messages in subsequently rounds
                if (sumOfM == message)
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
                if (round != 1 && (sumOfT == 0 || sumOfO == messagesSentInPreviousRounds.get(round/2))) {
                    // TODO: Check if this verification is needed
                    if (realRound) {
                        // We have to re-do the "father" round in order to expect that no all nodes involved in the collision re-send their message in the same round
                        // Add the "father" round to happen after this one
                        addRoundToHappenFirst(nextRoundsToHappen, round/2);
                        // Remove the virtual round related to this problematic round
                        removeRoundToHappen(nextRoundsToHappen, round+1);
                        // Sort the rounds again
                        // TODO: See if this really improves something or is not necessary
                        nextRoundsToHappen.sort(null);
                        // As we removed the next round from happening, we have to reassign the sending round to the "father" round once more
                        if (nextRoundAllowedToSend == round+1 || nextRoundAllowedToSend == round)
                            nextRoundAllowedToSend = round/2;
                    }
                }
                // In either re-sending modes, a "normal" collision can be produced
                // <sumOfT> > 1 => A Collision was produced
                else {
                    // Check if my message was involved in the collision, checking if in this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        // Check in which mode of re-sending my message we are
                        // Non probabilistic mode (see Reference for more information)
                        if (nonProbabilistic) {
                            // Calculate average message, if my message is below that value i re-send in the round (2*round)
                            if (message < sumOfM / sumOfT) {
                                nextRoundAllowedToSend = 2 * round;
                            }
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

            // Print a blank line
            System.out.println();

            // Prevent infinite loops
            /*if (round >= Math.pow(2, collisionSize)*4)
                finished = true;*/

        }

        // "Stop" the clock
        long t2 = System.nanoTime();

        // Calculate total time of execution and print it
        long total_time = t2-t1;
        System.out.println("Total Time: " + total_time + " nanoseconds");

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

        // Print all the messages received in this session
        System.out.println("\nMessages received: ");
        messagesReceived.forEach(System.out::println);

    }

    // Remove a round to happen afterwards
    private void removeRoundToHappen(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.removeFirstOccurrence(round);
    }

    // Add a round to happen immediately after the running one
    private void addRoundToHappenFirst(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.addFirst(round);
    }

    // Add two rounds to happen afterwards (they are added at the end of the LinkedList)
    private void addRoundsToHappenNext(LinkedList<Integer> nextRoundsToHappen, int firstRoundToAdd, int secondRoundToAdd) {
        nextRoundsToHappen.add(firstRoundToAdd);
        nextRoundsToHappen.add(secondRoundToAdd);
    }

    // Create all the requestors (the quantity depends on the index of the node) socket necessary to run the protocol (see Reference for more information)
    private ZMQ.Socket[] initializeRequestorsArray(int nodeIndex, ZContext context) {
        // Create an array of sockets
        ZMQ.Socket[] requestors = null;
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != dcNetSize) {
            // Initialize the array with exactly (<n> - <nodeIndex>) sockets
            requestors = new ZMQ.Socket[dcNetSize - nodeIndex];
            for (int i = 0; i < requestors.length; i++) {
                // Create the REQ socket
                requestors[i] = context.createSocket(ZMQ.REQ);
                // Connect this REQ socket to his correspondent REP socket of another node
                requestors[i].connect("tcp://" + directory.get(nodeIndex + i + 1) + ":" + (7000 + nodeIndex - 1));
            }
        }
        // Return the array with the requestor sockets
        return requestors;
    }

    // Create all the repliers (the quantity depends on the index of the node) socket necessary to run the protocol (see Reference for more information)
    private ZMQ.Socket[] initializeRepliersArray(int nodeIndex, ZContext context) {
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
        return repliers;
    }

    // Bind the sender port of the PUB socket (9000 by default)
    private void bindSenderPort(ZMQ.Socket sender) {
        sender.bind("tcp://*:9000");
    }

    // Synchronize the nodes of the room using the replier and requestor socket of each of the nodes (see Reference for more information)
    private void synchronizeNodes(int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors) {
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a message
                replier.recv(0);
                // When the replier receives the message, replies with another message
                replier.send("", 0);
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != dcNetSize)
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a message
                requestor.send("".getBytes(), 0);
                // The requestor waits to receive a reply by the correspondent replier
                requestor.recv(0);
            }
    }

    // Get the LAN IP address of the node
    public static String getLocalNetworkIp() {
        String networkIp = "";
        InetAddress ip;
        try {
            ip = InetAddress.getLocalHost();
            networkIp = ip.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return networkIp;
    }
}
