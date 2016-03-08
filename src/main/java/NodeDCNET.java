import com.google.gson.Gson;
import org.w3c.dom.Node;
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
    The nodes that are created by this class are run all in the same
    computer, running on different ports.
*/

class NodeDCNET implements ZThread.IAttachedRunnable {

    // private final int SYNC = 0;
    private final int DCNET = 1;

    private int dcNetSize;
    private final String myIp;
    private final String name;
    private final int message;
    private int nodeIndex;
    private final boolean NONPROBABILISTIC = false;
    private final String directoryIp;

    private static HashMap<Integer, String> directory = new HashMap<>();

    public NodeDCNET(String myIp, String name, String message, String directoryIp) {
        this.myIp = myIp;
        this.name = name;
        this.message = Integer.parseInt(message);
        this.directoryIp = directoryIp;
    }

    // Usage: ./gradlew run -PappArgs=[<message>,<directoryIP>]
    public static void main(String[] args) throws IOException {
        String myIp = getLocalNetworkIp();
        System.out.println("my IP: " + myIp + "\n");
        new NodeDCNET(myIp, "Node", args[0], args[1]).createNode();
    }

    // Receiver Thread
    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {

        // Create the receiver socket that work as a subscriber
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Set size of the dcnet room
        int dcNetRoomSize = (int) args[0];

        // Connect as a subscriber to each of the nodes on the DC-NET room
        connectReceiverThread(receiver, dcNetRoomSize);

        // Subscribe to whatever the nodes say
        receiver.subscribe("".getBytes());

        // Read from other nodes
        while (!Thread.currentThread().isInterrupted()) {

            String inputFromSender = pipe.recvStr();

            if (inputFromSender.equals("FINISHED"))
                break;

            int round = Integer.parseInt(inputFromSender);

            // Wait for sender thread let know that a new round will begin, if it is a virtual round, i wait two times
            if (round != 1 && round%2 != 0) {
                continue;
            }

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

    private void connectReceiverThread(ZMQ.Socket receiver, int dcNetSize) {
        for (int i = 1; i <= dcNetSize; i++) {
            receiver.connect("tcp://" + directory.get(i) + ":9000");
        }
    }

    // Sender Thread and Collision Resolution Protocol
    public void createNode() throws IOException {

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Create the sender socket that works as a publisher
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);

        // Bind sender port in 9000
        bindSenderPort(sender);

        // Create Directory Subscriber and connect to 5555 port
        ZMQ.Socket directorySubscriber = context.createSocket(ZMQ.SUB);
        directorySubscriber.connect("tcp://" + directoryIp + ":5555");
        directorySubscriber.subscribe("".getBytes());

        // Create Directory Push and connect to 5554 port
        ZMQ.Socket directoryPush = context.createSocket(ZMQ.PUSH);
        directoryPush.connect("tcp://" + directoryIp + ":5554");

        // Send my IP to the Directory
        directoryPush.send(myIp);

        // Wait message from the Directory with all the {index,ip} pairs
        String directoryJson = directorySubscriber.recvStr();
        Directory directory = new Gson().fromJson(directoryJson, Directory.class);
        for (int i = 0; i < directory.nodes.length; i++) {
            NodeDCNET.directory.put(directory.nodes[i].index, directory.nodes[i].ip);
        }

        // Rescue index (key) of the node given my ip (value)
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

        System.out.println("Number of nodes: " + dcNetSize);
        System.out.println("My index is: " + nodeIndex);

        // We need to connect every pair of nodes in order to synchronize the sending of values at the beginning of each round
        // For this, we need that in every pair of nodes there will be one requestor and one replier
        // In every pair of nodes {i,j} where i<j, node i will work as a requestor and node j will work as a replier
        // Create array of sockets that will work as repliers and requestors
        ZMQ.Socket[] repliers = initializeRepliersArray(nodeIndex, context);
        ZMQ.Socket[] requestors = initializeRequestorsArray(nodeIndex, context);

        // Throw receiver thread which runs the method 'run' described above
        ZMQ.Socket receiverThread = ZThread.fork(context, new NodeDCNET(this.myIp, this.name, "" + this.message, this.directoryIp), dcNetSize);

        // Sleep to overlap slow joiner problem
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create OutputMessage object
        OutputMessage outputMessage = new OutputMessage();
        outputMessage.setSenderId("Node_" + nodeIndex);
        outputMessage.setCmd(DCNET);

        // This is the actual message that the node wants to communicate (<m>)
        int message = this.message;
        if (message == 0) {
            outputMessage.setMessage(0);
        }
        else {
            outputMessage.setMessage(message*(dcNetSize+1) + 1);
        }
        String outputMessageJson = new Gson().toJson(outputMessage);

        // Print message to send
        System.out.println("\nm_" + nodeIndex + " = " + message + "\n");

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Index to know in what round we are
        int round;
        boolean realround;

        // Index to know in which round i'm allowed to resend my message
        int nextRoundAllowedToSend = 1;

        // How many messages are being involved in the first collision
        int collisionSize = 0;

        // Store messages sent in previous rounds in order to build the message in the virtual rounds
        Dictionary<Integer, Integer> messagesSentInPreviousRounds = new Hashtable<>();

        // Count how many messages were sent without collisions. When this number equals the collision size, the first collision was resolved
        int messagesSentWithNoCollisions = 0;

        // Variable to see if the first collision was solved or not
        boolean finished = false;

        // Variable to store the next rounds that are allow to happen (this is use to skip rounds that no node will send a value)
        LinkedList<Integer> nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);

        // Create the zeroMessage which is used several times on the protocol
        String zeroMessageJson = new Gson().toJson(new OutputMessage("Node_" + nodeIndex, DCNET, 0));

        // Store all the messages received in this round to show them later
        List<Integer> messagesReceived = new LinkedList<>();

        long t1 = 0;

        // Begin the collision resolution protocol
        while (!Thread.currentThread().isInterrupted()) {

            // Synchronize nodes at the beginning of each round
            synchronizeNodes(nodeIndex, repliers, requestors);

            if (finished) {
                receiverThread.send("FINISHED");
                receiverThread.recvStr();
                break;
            }
            else {
                // Get actual round to play and send this round to the receiver thread
                round = nextRoundsToHappen.removeFirst();
                receiverThread.send("" + round);
            }

            if (round == 1)
                t1 = System.currentTimeMillis();

            // PRINTING INFO ABOUT THE ROUND
            System.out.println("ROUND " + round);

            // Variables to store the resulting message of the round
            int sumOfM, sumOfT;
            int sumOfO = 0;

            // REAL ROUND
            if (round == 1 || round%2 == 0) {
                realround = true;
                System.out.println("REAL ROUND");

                if (messageTransmitted) {
                    sender.send(zeroMessageJson);
                }

                else if (nextRoundAllowedToSend == round) {
                    sender.send(outputMessageJson);
                }
                else {
                    sender.send(zeroMessageJson);
                }

                // Receive information from the receiver thread
                // Count how many messages were receive from the receiver thread. When this number equals <dcNetSize> i've received all the messages in this round
                int messagesReceivedInThisRound = 0;
                while (messagesReceivedInThisRound < dcNetSize) {
                    String messageReceivedFromReceiverThread = receiverThread.recvStr();
                    int incomingOutputMessage = Integer.parseInt(messageReceivedFromReceiverThread);
                    sumOfO += incomingOutputMessage;
                    messagesReceivedInThisRound++;
                }

            }

            // VIRTUAL ROUND
            else {
                realround = false;
                System.out.println("VIRTUAL ROUND");

                // Recover messages sent in rounds 2k and k
                int sumOfOSentInRound2K = messagesSentInPreviousRounds.get(round - 1);
                int sumOfOSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                // If not, is a virtual round that needs to happen, so calculate the resulting message
                sumOfO = sumOfOSentInRoundK - sumOfOSentInRound2K;
            }

            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, sumOfO);

            // Divide sumOfO in sumOfM and sumOfT
            sumOfM = sumOfO/(dcNetSize + 1);
            sumOfT = sumOfO - (sumOfM*(dcNetSize + 1));

            // Display round message
            System.out.println("C_" + round +  " = (" + sumOfM + "," + sumOfT + ")");

            // Assign the size of the collision produced in the first round
            if (round == 1) {
                collisionSize = sumOfT;
                if (collisionSize == 0) {
                    System.out.println("NO MESSAGES WERE SENT");
                    finished = true;
                    continue;
                }
            }

            // NO COLLISION ROUND => <sumOfT> = 1
            if (sumOfT == 1) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // Add message received in this round
                messagesReceived.add(sumOfM);

                // If the message that went through equals mine, my message was transmitted
                if (sumOfM == message)
                    messageTransmitted = true;

                // If the number of messages that went through equals the collision size, the collision was completely resolved
                if (messagesSentWithNoCollisions == collisionSize) {
                    finished = true;
                }
            }

            // COLLISION OR NO MESSAGES SENT IN THIS ROUND => <sumOfT> != 1
            else {
                if (sumOfT == 0) {
                    if (realround) {
                        // There are no messages sent in a real round, so we do it once again
                        addRoundToHappenFirst(nextRoundsToHappen, round);
                        if (nextRoundAllowedToSend == round) {
                            if (NONPROBABILISTIC) {
                                // See if i need to send in the next (2*round) round, checking the average condition
                                if (message < sumOfM / sumOfT) {
                                    nextRoundAllowedToSend = 2 * round;
                                }
                                // If not, i'm "allowed to send" in the (2*round + 1) round, which will be a virtual round
                                else {
                                    nextRoundAllowedToSend = 2 * round + 1;
                                }
                            } else {
                                // Throw a coin to see if a send in the round (2*round) or (2*round + 1)
                                int coin = new SecureRandom().nextInt(2);
                                if (coin == 0) {
                                    nextRoundAllowedToSend = 2 * round;
                                } else {
                                    nextRoundAllowedToSend = 2 * round + 1;
                                }
                            }
                        }
                    }
                }
                else {
                    // New collision produced, it means that <sumOfT> > 1
                    // Check if my message was involved in the collision, seeing that this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        if (NONPROBABILISTIC) {
                            // See if i need to send in the next (2*round) round, checking the average condition
                            if (message < sumOfM / sumOfT) {
                                nextRoundAllowedToSend = 2 * round;
                            }
                            // If not, i'm "allowed to send" in the (2*round + 1) round, which will be a virtual round
                            else {
                                nextRoundAllowedToSend = 2 * round + 1;
                            }
                        } else {
                            // Throw a coin to see if a send in the round (2*round) or (2*round + 1)
                            int coin = new SecureRandom().nextInt(2);
                            if (coin == 0) {
                                nextRoundAllowedToSend = 2 * round;
                            } else {
                                nextRoundAllowedToSend = 2 * round + 1;
                            }
                        }
                    }
                    // Add 2k and 2k+1 rounds to future plays
                    addRoundsToHappenNext(nextRoundsToHappen, 2 * round, 2 * round + 1);
                }
            }

            System.out.println();

            // Prevent infinite loops
            /*if (round >= Math.pow(2, collisionSize)*4)
                finished = true;*/

        }

        long t2 = System.currentTimeMillis();

        long total_time = t2-t1;
        System.out.println("Total Time: " + total_time + " milliseconds");

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

        System.out.println("\nMessages received: ");
        messagesReceived.forEach(System.out::println);

    }

    private void addRoundToHappenFirst(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.addFirst(round);
    }

    private void addRoundsToHappenNext(LinkedList<Integer> nextRoundsToHappen, int firstRoundToAdd, int secondRoundToAdd) {
        nextRoundsToHappen.add(firstRoundToAdd);
        nextRoundsToHappen.add(secondRoundToAdd);
    }

    private ZMQ.Socket[] initializeRequestorsArray(int nodeIndex, ZContext context) {
        ZMQ.Socket[] requestors = null;
        if (nodeIndex != dcNetSize) {
            requestors = new ZMQ.Socket[dcNetSize - nodeIndex];
            for (int i = 0; i < requestors.length; i++) {
                requestors[i] = context.createSocket(ZMQ.REQ);
                requestors[i].connect("tcp://" + directory.get(nodeIndex + i + 1) + ":" + (7000 + nodeIndex - 1));
            }
        }
        return requestors;
    }

    private ZMQ.Socket[] initializeRepliersArray(int nodeIndex, ZContext context) {
        ZMQ.Socket[] repliers = null;

        if (nodeIndex != 1) {
            repliers = new ZMQ.Socket[nodeIndex-1];

            for (int i = 0; i < repliers.length; i++) {
                repliers[i] = context.createSocket(ZMQ.REP);
                repliers[i].bind("tcp://*:" + (7000+i));
            }

        }

        return repliers;

    }

    private void bindSenderPort(ZMQ.Socket sender) {
        sender.bind("tcp://*:9000");
    }

    private void synchronizeNodes(int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors) {
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                replier.recv(0);
                replier.send("", 0);
            }

        if (nodeIndex != dcNetSize)
            for (ZMQ.Socket requestor : requestors) {
                requestor.send("".getBytes(), 0);
                requestor.recv(0);
            }
    }

    private static String cut_ip(String ip) {
        String[] numbers = ip.split("\\.");
        return "" + numbers[0] + "." + numbers[1] + "." + numbers[2] + ".";
    }

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

    /* private void waitForAllSubscribers(ZMQ.Socket receiverThread, ZMQ.Socket sender, int nodeIndex) {
        // Let know the receiver thread what is my nodeIndex
        receiverThread.send("" + nodeIndex);

        // Set timeout on the receiver thread
        receiverThread.setReceiveTimeOut(2000);

        // Store the message received from receiver thread
        String inputSyncMessage;

        while (true) {
            // Send nodeIndex to the rest of the room
            sender.send("" + nodeIndex);

            // While the receiver doesn't inform me i send again my nodeIndex to the rest of the room
            while ((inputSyncMessage = receiverThread.recvStr()) == null)
                sender.send("" + nodeIndex);

            // If the receiver tells me we are ready to go, break the cycle and continue
            if (inputSyncMessage.equals("ready")) {
                sender.send("" + nodeIndex);
                sender.send("go" + nodeIndex);
                for (int i = 0; i < dcNetSize; i++)
                    sender.send("r" + (i+1) + "#" + nodeIndex);
            }

            else if (inputSyncMessage.equals("all ready")) {
                break;
            }

            // If not, it means that the receiver received a message from other node, so i let know to the rest of the room that i'm already connected to them
            else
                // "rX#Y" means that node Y already received message from node X
                sender.send("r" + inputSyncMessage + "#" + nodeIndex);
        }

        // Set timeout to infinity to continue the protocol
        receiverThread.setReceiveTimeOut(-1);
    } */

    /* private void waitForAllPublishers(ZMQ.Socket pipe, ZMQ.Socket receiver) {
        // Receive message from sender that indicates our nodeIndex
        int nodeIndex = Integer.parseInt(pipe.recvStr());

        // Store the message received from receiver thread
        String inputSyncMessage;

        // Set time out of the receiver socket
        receiver.setReceiveTimeOut(2000);

        // Array to store what nodes received my message
        boolean[] nodesConnectedtoMe = new boolean[dcNetSize];

        // Array to store what nodes i'm connected to
        boolean[] nodesConnectedTo = new boolean[dcNetSize];

        // Array to store nodes ready
        boolean[] nodesReady = new boolean[dcNetSize];

        for (boolean value : nodesConnectedtoMe)
            value = false;
        for (boolean value : nodesConnectedTo)
            value = false;

        // Variable to know if i'm connected to the rest of the room
        boolean connectedToAllRoom = false;

        // Variable to know if all the room is connected to me
        boolean allRoomConnectedToMe = false;

        // Variable to know if all the room is connected between them
        boolean allRoomConnected = false;

        while (true) {
            // Wait until receive any message from any node of the room
            while ((inputSyncMessage = receiver.recvStr()) == null)
                ;

            // If the first character is not an 'r' it means that i can hear a certain node that is transmitting
            if (inputSyncMessage.charAt(0) != 'r' && inputSyncMessage.charAt(0) != 'g') {
                // <inputSyncMessage> = node that I can hear
                // Let know to sender thread that i can hear a certain node stored on <inputSyncMessage>
                pipe.send(inputSyncMessage);

                if (!nodesConnectedTo[Integer.parseInt(inputSyncMessage) - 1]) {
                    nodesConnectedTo[Integer.parseInt(inputSyncMessage) - 1] = true;
                    System.out.println("Node " + nodeIndex + " can hear node " + inputSyncMessage);
                }
            }

            else if (inputSyncMessage.charAt(0) == 'g') {
                int nodeReady = Integer.parseInt(inputSyncMessage.substring(2,3));
                nodesReady[nodeReady - 1] = true;
                for (boolean value : nodesReady) {
                    if (value)
                        allRoomConnected = true;
                    else {
                        allRoomConnected = false;
                        break;
                    }
                }
            }

            // If the first character is an 'r', it means that is a response that a node can hear a certain other node
            // <inputSyncMessage> = 'rX#Y' => node Y can hear node X
            else {
                // Separate values that what node is being heard and what node is the one that is hearing it
                int nodeThatIsHeard = Integer.parseInt(inputSyncMessage.substring(1, 2));
                int nodeThatIsHearing = Integer.parseInt(inputSyncMessage.substring(3, 4));

                // See if other node is being hearing me
                if (nodeThatIsHeard == nodeIndex) {
                    if (!nodesConnectedtoMe[nodeThatIsHearing - 1]) {
                        nodesConnectedtoMe[nodeThatIsHearing - 1] = true;
                        System.out.println("Node " + nodeIndex + " is being heard by node " + nodeThatIsHearing);
                    }
                }
            }

            // To be ready i need that all the nodes are connected to me and i am connected to all the nodes
            // Check that all the nodes are connected to me
            for (boolean value : nodesConnectedtoMe) {
                if (value)
                    connectedToAllRoom = true;
                else {
                    connectedToAllRoom = false;
                    break;
                }
            }

            // Check that i am connected to all the nodes
            for (boolean value : nodesConnectedTo) {
                if (value)
                    allRoomConnectedToMe = true;
                else {
                    allRoomConnectedToMe = false;
                    break;
                }
            }

            // If both values are true, it means that i am ready to go
            if (connectedToAllRoom && allRoomConnectedToMe) {
                pipe.send("ready");
                // ¿Let know to the rest of the room that i'm ready?
            }

            if (allRoomConnected) {
                pipe.send("all ready");
                break;
            }


        }

        receiver.setReceiveTimeOut(-1);

    } */
