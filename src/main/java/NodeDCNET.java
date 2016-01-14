import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

class NodeDCNET implements ZThread.IAttachedRunnable {

    private final int dcNetSize = 3;
    private final String networkIp;
    private final String name;

    public NodeDCNET(String networkIp, String name) {
        this.networkIp = networkIp;
        this.name = name;
    }

    public static void main(String[] args) throws IOException {
        new NodeDCNET("127.0.0.1", "Node " + args[0]).createNode();
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

        // Sum of all the messages received in this round
        int sumOfMessagesReceived= 0;

        // Number of messages received in this round
        int numberOfMessagesReceived = 0;

        // Number of Non-Zero messages received in this round
        int numberOfNonZeroMessagesReceived = 0;

        // Read from other nodes
        while (!Thread.currentThread().isInterrupted()) {

            // Message received from other node
            // This message has the next format: "<m>#<t>" where <m> is the message and <t> is either 1 or 0
            String inputMessage = receiver.recvStr().trim();

            // Split the message in two: messageAndNumber[0]=<m> and messageAndNumber[1]=<t>
            String[] messageAndNumber = inputMessage.split("#");

            // Add <m> tp the sum of all the messages received in this round
            sumOfMessagesReceived += Integer.parseInt(messageAndNumber[0]);

            // Increase the number of messages received in this round
            numberOfMessagesReceived++;

            // Add <t> to the number of non-zero messages received. Only will increase when <t>=1
            numberOfNonZeroMessagesReceived += Integer.parseInt(messageAndNumber[1]);

            // If the number of messages received in this round equals the size of the DC-NET room, it means that i received all of them
            if (numberOfMessagesReceived == dcNetSize) {

                // If the number of non-zero messages received equals 1, it means that there is no collision in this round
                if (numberOfNonZeroMessagesReceived == 1) {
                    // TODO: No Collision Round
                    pipe.send("" + sumOfMessagesReceived, 0);
                    break;
                }
                // The non-zero messages is not 1, so it means that there's a collision of two or more messages
                else {
                    // TODO: Round with Collision
                    int averageMessage = sumOfMessagesReceived / numberOfMessagesReceived;
                    pipe.send("" + averageMessage, 1);
                }

                // End of the round. Reset all the values to 0 and start a new round
                sumOfMessagesReceived = 0;
                numberOfMessagesReceived = 0;
                numberOfNonZeroMessagesReceived = 0;

            }

        }

        // Close receiver thread
        receiver.close();

    }

    // Sender Thread
    public void createNode() throws IOException {

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Throw receiver thread which runs the method run described above
        ZMQ.Socket receiverThread = ZThread.fork(context, new NodeDCNET(this.networkIp, this.name), networkIp);

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
        ZMQ.Socket[] repliers;
        ZMQ.Socket[] requestors;

        // Initialize repliers array
        // If my index is 0, i will only have requestors and none replier
        if (nodeIndex != 0) {
            // If my index is <nodeIndex>, i will have to create <nodeIndex> repliers
            repliers = new ZMQ.Socket[nodeIndex];

            // Iterate in repliers array in order to create the socket, bind the port and synchronize for the first time
            for (int i = 0; i < repliers.length; i++) {
                // Create the replier socket
                repliers[i] = context.createSocket(ZMQ.REP);

                // Bind the replier socket to the corresponding port, that is at least 7000
                repliers[i].bind("tcp://*:700" + (nodeIndex - 1 + i));

                // First synchronization to wait nodes to be connected
                repliers[i].recv(0);
                repliers[i].send("", 0);
            }
        }

        // Initialize requestors array
        // If my index is the last, i will only have repliers and none requestor
        if (nodeIndex != dcNetSize - 1) {
            // If my index is <nodeIndex>, i will have to create (<dcNetSize>-<nodeIndex>-1) requestors
            requestors = new ZMQ.Socket[dcNetSize - nodeIndex - 1];

            // Iterate in requestors array in order to create the socket, bind the port and synchronize for the first time
            for (int i = 0; i < requestors.length; i++) {
                // Create the requestor socket
                requestors[i] = context.createSocket(ZMQ.REQ);

                // Bind the requestor socket to the corresponding port, that is at least 7000
                requestors[i].connect("tcp://*:700" + (nodeIndex*2 + i)); // <-- Check this with examples with more than 3 nodes!

                // First synchronization to wait nodes to be connected
                requestors[i].send("".getBytes(), 0);
                requestors[i].recv(0);
            }
        }

        // This is the actual message that the node want to communicate (<m>)
        int outputNumericMessage = new Random().nextInt(100);

        // This message <m> must be appended to #1, forming M = <m>#1
        String outputMessage = outputNumericMessage + "#1";

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Write to the other nodes at the beginning of the round
        while (!Thread.currentThread().isInterrupted()) {
            // Sending message M to the rest of the room
            sender.send(outputMessage);

            // Receive information from the receiver thread in order to know how to proceed in the next round
            // Check if there were or not collisions
            // Receive the message from receiver thread with flag = 0
            String messageReceivedFromReceiverThread = receiverThread.recvStr(0);

            // If it's not null, it means that there were no collisions
            if (messageReceivedFromReceiverThread != null) {
                // Check again if the message receive from receiver thread equals the message <m> that i sent
                if (messageReceivedFromReceiverThread.equals("" + outputNumericMessage)) {
                    System.out.println("My message was received!");
                    messageTransmitted = true;
                }
            }

            // Check if there were collisions
            else {
                // Receive the message from receiver thread with flag = 1
                messageReceivedFromReceiverThread = receiverThread.recvStr(1);

                // If it's not null, it means that there were collisions
                if (messageReceivedFromReceiverThread != null) {
                    // If my message is below the average, i send again the same message in the next round
                    if (outputNumericMessage < Integer.parseInt(messageReceivedFromReceiverThread)) {
                        continue;
                    }

                    // If my message is not below the average, i send the message 0#0 in the next round
                    else {
                        outputMessage = "0#0";
                    }
                }
            }

            // Check if my message was transmitted, i stop the application
            if (messageTransmitted)
                break;

            new BufferedReader(new InputStreamReader(System.in)).readLine(); // <-- Used to stop the while

        }

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

    }

}
