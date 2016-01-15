import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Dictionary;
import java.util.Hashtable;

/*
    This application runs a collision resolution protocol, in order
    to use within a DC-NET.
    The nodes that are created by this class are run all in the same
    computer, running on different ports.
*/

class NodeDCNET implements ZThread.IAttachedRunnable {

    private final int dcNetSize;
    private final String networkIp;
    private final String name;
    private final int outputNumericMessage;

    public NodeDCNET(String networkIp, String name, String outputNumericMessage, String dcNetSize) {
        this.networkIp = networkIp;
        this.name = name;
        this.outputNumericMessage = Integer.parseInt(outputNumericMessage);
        this.dcNetSize = Integer.parseInt(dcNetSize);
    }

    public static void main(String[] args) throws IOException {
        // Usage: ./gradlew run <message> <numberOfNodes>
        new NodeDCNET("127.0.0.1", "Node", args[0], args[1]).createNode();
    }

    // Receiver Thread
    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {

        // Get the Network IP where all the nodes are participating
        String network_ip = (String) args[0];

        // Create the receiver socket that works as a subscriber
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Connect as a subscriber to all the nodes on the DC-NET room, from port 9001 to (9000 + <dcNetSize>)
        for (int port = 9001; port < 9001 + dcNetSize; port++) {
            receiver.connect("tcp://" + network_ip + ":" + port);
        }

        // Subscribe to whatever the other nodes say
        receiver.subscribe("".getBytes());

        // Read from other nodes
        while (!Thread.currentThread().isInterrupted()) {
            System.out.println("Waiting to receive message...");

            // Receive message
            String inputMessage = receiver.recvStr().trim();

            System.out.println("Receive message from other node: " + inputMessage);

            // Send the message received to the publisher that handles the collision
            pipe.send(inputMessage);

            System.out.println("Sent message to the sender thread: " + inputMessage);
        }

        // Close receiver thread
        receiver.close();

    }

    // Sender Thread
    public void createNode() throws IOException {

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Throw receiver thread which runs the method run described above
        ZMQ.Socket receiverThread = ZThread.fork(context, new NodeDCNET(this.networkIp, this.name, "" + this.outputNumericMessage, "" + this.dcNetSize), networkIp);

        // Create the sender socket that works as a publisher
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);

        // Index of current node
        int nodeIndex;

        // Explore in all the ports, starting from 9001, until find one available. This port will also used as the index for the node, which range will be: [1, ..., n]
        for (nodeIndex = 1; nodeIndex < dcNetSize + 1; nodeIndex++) {
            // Try to bind the sender to the port (9000 + <nodeIndex>).
            // If not, continue with the rest of the ports
            try {
                sender.bind("tcp://*:" + (9000 + nodeIndex));
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
        // If my index is 1, i will only have requestors and none replier
        if (nodeIndex != 1) {
            // If my index is <nodeIndex>, i will have to create (<nodeIndex>-1) repliers
            repliers = new ZMQ.Socket[nodeIndex-1];

            // Iterate in repliers array in order to create the socket and bind the port
            for (int i = 0; i < repliers.length; i++) {
                // Create the replier socket
                repliers[i] = context.createSocket(ZMQ.REP);

                // Bind the replier socket to the corresponding port
                int firstPortToBind = (7002 + ((nodeIndex*(nodeIndex-3))/2));
                repliers[i].bind("tcp://*:" + (firstPortToBind+i));
            }
        }

        // Initialize requestors array
        // If my index is the last one, i will only have repliers and none requestor
        if (nodeIndex != dcNetSize) {
            // If my index is <nodeIndex>, i will have to create (<dcNetSize>-<nodeIndex>) requestors
            requestors = new ZMQ.Socket[dcNetSize - nodeIndex];

            // Iterate in requestors array in order to create the socket and bind the port
            for (int i = 0; i < requestors.length; i++) {
                // Create the requestor socket
                requestors[i] = context.createSocket(ZMQ.REQ);

                // Bind the requestor socket to the corresponding port, that is at least 7001
                int portToConnect = ((((nodeIndex + i + 1)*(nodeIndex + i - 2))/2) + 7002) + nodeIndex - 1;
                requestors[i].connect("tcp://*:" + (portToConnect));
            }
        }

        // This is the actual message that the node wants to communicate (<m>)
        int outputNumericMessage = this.outputNumericMessage;

        // If <m>!=0 must be appended to #1, forming M = <m>#1. If not, M = 0#0
        String outputMessage;
        if (outputNumericMessage == 0)
            outputMessage = "0#0";
        else
            outputMessage = outputNumericMessage + "#1";

        System.out.println("m = " + outputNumericMessage);

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Index to know in what round we are
        int round = 1;

        // Index to know in which round i'm allowed to resend my message
        int nextRoundAllowedToSend = 1;

        // How many messages are being involved in the first collision
        int collisionSize = 0;

        Dictionary<Integer, String> messagesSentInPreviousRounds = new Hashtable<>();

        // Count how many messages were sent without collisions. When this number equals the collision size, the collision was resolved
        int messagesSentWithNoCollisions = 0;

        // Begin the collision resolution protocol
        while (!Thread.currentThread().isInterrupted()) {

            // Needed for the test, some configuration iterates at infinity
            if (round == 20)
                new BufferedReader(new InputStreamReader(System.in)).readLine();

            // Synchronize nodes at the beginning of each round
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

            System.out.println("ROUND " + round);

            // Variables to store the resulting message of the round
            // C = <sumOfM>#<sumOfT>
            int m, t, sumOfM = 0, sumOfT = 0;

            // REAL ROUND
            if (round == 1 || round%2 == 0) {
                System.out.println("REAL ROUND");

                // If my message was already transmitted i just send "0#0"
                if (messageTransmitted)
                    sender.send("0#0");

                // Sending message M to the rest of the room if i'm allowed to. If not, i send "0#0"
                if (nextRoundAllowedToSend == round && !messageTransmitted)
                    sender.send(outputMessage);
                else
                    sender.send("0#0");

                // Receive information from the receiver thread
                // Count how many messages were receive from the receiver thread. When this number equals <dcNetSize> i've received all the messages in this round
                int messagesReceivedInThisRound = 0;
                while (messagesReceivedInThisRound < dcNetSize) {
                    String messageReceivedFromReceiverThread = receiverThread.recvStr();
                    m = Integer.parseInt(messageReceivedFromReceiverThread.split("#")[0]);
                    t = Integer.parseInt(messageReceivedFromReceiverThread.split("#")[1]);
                    sumOfM += m;
                    sumOfT += t;
                    messagesReceivedInThisRound++;
                }

                // Assign the size of the collision produced in the first round
                if (round == 1) {

                    collisionSize = sumOfT;
                    if (collisionSize == 0) {
                        System.out.println("NO COLLISION PRODUCED");
                        new BufferedReader(new InputStreamReader(System.in)).readLine();
                    }

                }

                System.out.println("C = " + sumOfM + "#" + sumOfT);

                // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
                messagesSentInPreviousRounds.put(round, "" + sumOfM + "#" + sumOfT);

            }

            // VIRTUAL ROUND
            else {
                System.out.println("VIRTUAL ROUND");

                // Recover messages sent in rounds 2k and k
                String messageSentInRound2K = messagesSentInPreviousRounds.get(round-1);
                String messageSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                // Divide messages received in rounds 2k and k in <sumOfM> and <sumOfT> for each round
                int sumOfMInRound2k = Integer.parseInt(messageSentInRound2K.split("#")[0]);
                int sumOfTInRound2k = Integer.parseInt(messageSentInRound2K.split("#")[1]);
                int sumOfMInRoundK = Integer.parseInt(messageSentInRoundK.split("#")[0]);
                int sumOfTInRoundK = Integer.parseInt(messageSentInRoundK.split("#")[1]);

                // If any of the messages recover for this rounds was 0, it means that this round it doesn't really happens
                if (sumOfMInRound2k == 0 || sumOfMInRoundK == 0) {
                    sumOfM = 0;
                    sumOfT = 0;
                }

                // If not, is a virtual round that needs to happen, so calculate the resulting message
                else {
                    sumOfM = sumOfMInRoundK - sumOfMInRound2k;
                    sumOfT = sumOfTInRoundK - sumOfTInRound2k;
                }

                System.out.println("C = " + sumOfM + "#" + sumOfT);

                // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
                messagesSentInPreviousRounds.put(round, "" + sumOfM + "#" + sumOfT);

            }

            // Already received the information, either from real round or virtual round

            // NO COLLISION ROUND => <sumOfT> = 1
            if (sumOfT == 1) {

                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // If the message that went through equals mine, my message was transmitted
                if (sumOfM == outputNumericMessage) {
                    messageTransmitted = true;
                }

                // If the number of messages that went through equals the collision size, the collision was completely resolved
                if (messagesSentWithNoCollisions == collisionSize) {
                    System.out.println("Finished!");
                    new BufferedReader(new InputStreamReader(System.in)).readLine();
                }
            }

            // COLLISION OR NO MESSAGES SENT IN THIS ROUND => <sumOfT> != 1
            else {

                // New collision produced, it means that <sumOfT> > 1
                if (sumOfT != 0) {

                    // Check if my message was involved in the collision, seeing that this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {

                        // See if i need to send in the next (2*round) round, checking the average condition
                        if (outputNumericMessage < sumOfM / sumOfT) {
                            nextRoundAllowedToSend = 2 * round;
                        }

                        // If not, i'm "allowed to send" in the (2*round + 1) round, which will be a virtual round
                        else {
                            nextRoundAllowedToSend = 2 * round + 1;
                        }

                    }

                }

            }

            // At the end of the round, i increase the round number and continue
            round++;
            System.out.println();

        }

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

    }

}
