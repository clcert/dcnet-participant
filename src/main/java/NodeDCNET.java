import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Random;

class NodeDCNET implements ZThread.IAttachedRunnable {

    private final int dcNetSize = 3;
    private final String networkIp;
    private final String name;
    private int outputNumericMessage;

    public NodeDCNET(String networkIp, String name, String outputNumericMessage) {
        this.networkIp = networkIp;
        this.name = name;
        this.outputNumericMessage = Integer.parseInt(outputNumericMessage);
    }

    public static void main(String[] args) throws IOException {
        new NodeDCNET("127.0.0.1", "Node", args[0]).createNode();
    }

    // Receiver Thread
    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {

        // Get the Network IP where all the nodes are participating
        String network_ip = (String) args[0];

        // Create the receiver socket that works as a subscriber
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Connect as a subscriber to all the nodes on the DC-NET room
        for (int port = 9000; port < 9000 + dcNetSize; port++) {
            receiver.connect("tcp://" + network_ip + ":" + port);
        }

        // Subscribe to whatever the other nodes say
        receiver.subscribe("".getBytes());

        // Read from other nodes
        while (!Thread.currentThread().isInterrupted()) {
            String inputMessage = receiver.recvStr().trim();
            pipe.send(inputMessage);
        }

        // Close receiver thread
        receiver.close();

    }

    // Sender Thread
    public void createNode() throws IOException {

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Throw receiver thread which runs the method run described above
        ZMQ.Socket receiverThread = ZThread.fork(context, new NodeDCNET(this.networkIp, this.name, "" + this.outputNumericMessage), networkIp);

        // Create the sender socket that works as a publisher
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);

        // Index of current node
        int nodeIndex;

        // Explore in all the ports, starting from 9000, until find one available. This port will also used as the index for the node (not related with the name of it)
        for (nodeIndex = 0; nodeIndex < dcNetSize; nodeIndex++) {
            // Try to bind the sender to the port 900<nodeIndex>. If not, continue with the rest of the ports
            try {
                sender.bind("tcp://*:900" + nodeIndex);
            } catch (Exception e) {
                continue;
            }
            // Already bound to a port, so break the cycle
            break;
        }

        // We need to connect every pair of nodes in order to synchronize the sending of values
        // For this, we need that in every pair of nodes there will be one requestor and one replier
        // In every pair of nodes {i,j} where i<j, node i will work as a requestor and node j will work as a replier
        // Create array of sockets that will work as repliers and requestors
        ZMQ.Socket[] repliers = null;
        ZMQ.Socket[] requestors = null;

        // Initialize repliers array
        // If my index is 0, i will only have requestors and none replier
        if (nodeIndex != 0) {
            // If my index is <nodeIndex>, i will have to create <nodeIndex> repliers
            repliers = new ZMQ.Socket[nodeIndex];

            // Iterate in repliers array in order to create the socket and bind the port
            for (int i = 0; i < repliers.length; i++) {
                // Create the replier socket
                repliers[i] = context.createSocket(ZMQ.REP);

                // Bind the replier socket to the corresponding port, that is at least 7000
                repliers[i].bind("tcp://*:700" + (nodeIndex - 1 + i));
            }
        }

        // Initialize requestors array
        // If my index is the last, i will only have repliers and none requestor
        if (nodeIndex != dcNetSize - 1) {
            // If my index is <nodeIndex>, i will have to create (<dcNetSize>-<nodeIndex>-1) requestors
            requestors = new ZMQ.Socket[dcNetSize - nodeIndex - 1];

            // Iterate in requestors array in order to create the socket and bind the port
            for (int i = 0; i < requestors.length; i++) {
                // Create the requestor socket
                requestors[i] = context.createSocket(ZMQ.REQ);

                // Bind the requestor socket to the corresponding port, that is at least 7000
                requestors[i].connect("tcp://*:700" + (nodeIndex*2 + i)); // <-- Check this with examples with more than 3 nodes!
            }
        }

        // This is the actual message that the node want to communicate (<m>)
        int outputNumericMessage = this.outputNumericMessage;

        // This message <m> must be appended to #1, forming M = <m>#1
        String outputMessage = outputNumericMessage + "#1";

        System.out.println("M = " + outputNumericMessage);

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Index to know in what round we are
        int round = 1;

        // Index to know in which round i'm allowed to resend my message
        int nextRoundAllowedToSend = 1;

        Dictionary<Integer, String> messagesSentInPreviousRounds = new Hashtable<>();

        int messagesSentWithNoCollisions = 0;

        // Write to the other nodes at the beginning of the round
        while (!Thread.currentThread().isInterrupted()) {
            // Synchronize nodes at the beginning of each round
            if (nodeIndex != 0) {
                for (ZMQ.Socket replier : repliers) {
                    replier.recv(0);
                    replier.send("", 0);
                }
            }

            if (nodeIndex != dcNetSize - 1) {
                for (ZMQ.Socket requestor : requestors) {
                    requestor.send("".getBytes(), 0);
                    requestor.recv(0);
                }
            }

            System.out.println("ROUND " + round);

            int m, t;
            int sumOfM = 0, sumOfT = 0;

            // REAL ROUND
            if (round == 1 || round%2 == 0) {
                System.out.println("REAL ROUND");
                // If my message was already transmitted i just send "0#0"
                if (messageTransmitted) {
                    sender.send("0#0");
                }

                // Sending message M to the rest of the room if i'm allowed to. If not, i send "0#0"
                if (nextRoundAllowedToSend == round && !messageTransmitted) {
                    sender.send(outputMessage);
                } else {
                    sender.send("0#0");
                }

                // Receive information from the receiver thread in order to know how to proceed in the next rounds
                int messagesReceivedInThisRound = 0;

                while (messagesReceivedInThisRound < dcNetSize) {
                    String messageReceivedFromReceiverThread = receiverThread.recvStr();
                    m = Integer.parseInt(messageReceivedFromReceiverThread.split("#")[0]);
                    t = Integer.parseInt(messageReceivedFromReceiverThread.split("#")[1]);
                    sumOfM += m;
                    sumOfT += t;
                    messagesReceivedInThisRound++;
                }

                System.out.println("C = " + sumOfM + "#" + sumOfT);
                messagesSentInPreviousRounds.put(round, "" + sumOfM + "#" + sumOfT);

            }

            // VIRTUAL ROUND
            else {
                System.out.println("VIRTUAL ROUND");

                String messageSentInRound2K = messagesSentInPreviousRounds.get(round-1);
                String messageSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                int sumOfMInRound2k = Integer.parseInt(messageSentInRound2K.split("#")[0]);
                int sumOfTInRound2k = Integer.parseInt(messageSentInRound2K.split("#")[1]);

                int sumOfMInRoundK = Integer.parseInt(messageSentInRoundK.split("#")[0]);
                int sumOfTInRoundK = Integer.parseInt(messageSentInRoundK.split("#")[1]);

                if (sumOfMInRound2k == 0 || sumOfMInRoundK == 0) {
                    sumOfM = 0;
                    sumOfT = 0;
                }
                else {
                    sumOfM = sumOfMInRoundK - sumOfMInRound2k;
                    sumOfT = sumOfTInRoundK - sumOfTInRound2k;
                }

                System.out.println("C = " + sumOfM + "#" + sumOfT);
                messagesSentInPreviousRounds.put(round, "" + sumOfM + "#" + sumOfT);

            }

            // Already received the information, either from real round or virtual round

            // NO COLLISION
            if (sumOfT == 1) {
                messagesSentWithNoCollisions++;
                if (sumOfM == outputNumericMessage) {
                    messageTransmitted = true;
                }
                else {
                    // ?
                }
                if (messagesSentWithNoCollisions == dcNetSize) {
                    System.out.println("FINISHED SOLVING THE COLLISION!");
                    new BufferedReader(new InputStreamReader(System.in)).readLine();
                }
            }

            // COLLISION OR NO MESSAGES SENT IN THIS ROUND
            else {
                if (sumOfT == 0) {
                    round++;
                    System.out.println();
                    continue;
                }
                else if (outputNumericMessage < sumOfM/sumOfT) {
                    nextRoundAllowedToSend = 2*round;
                }
            }

            round++;
            System.out.println();

        }

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

    }

}
