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

            // Number of nodes that have had their messages sent with no collisions
            int messagesSentWithNoCollisions = 0;

            // If the number of messages received in this round equals the size of the DC-NET room, it means that i received all of them
            if (numberOfMessagesReceived == dcNetSize) {

                // If the number of non-zero messages received equals 1, it means that there is no collision in this round
                if (numberOfNonZeroMessagesReceived == 1) {
                    // No Collision Round
                    // Send to the sender thread "$" + <sumOfMessagesReceived> which corresponds to a "flag" letting know that there were no collisions and the message <m> of one of the nodes
                    pipe.send("$" + sumOfMessagesReceived, 0);

                    // Increase the number of messages that had been sent with no collisions
                    messagesSentWithNoCollisions++;

                    // If the number of messages sent with no collisions equals the size of the room, it means that all the participants could sent their message
                    if (messagesSentWithNoCollisions == dcNetSize) {
                        System.out.println("FINISH!");
                        // TODO: Let know to the sender that the rounds are over
                        break;
                    }
                }

                // The non-zero messages is not 1, so it means that there's a collision of two or more messages
                else {
                    // Round with Collision
                    // Calculate average message and send it to the sender thread
                    int averageMessage = sumOfMessagesReceived / numberOfMessagesReceived;
                    pipe.send("%" + averageMessage, 0);
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
        int outputNumericMessage = new Random().nextInt(100);

        // This message <m> must be appended to #1, forming M = <m>#1
        String outputMessage = outputNumericMessage + "#1";

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Index to know in what round we are
        int round = 1;

        // Index to know in which round i'm allowed to resend my message
        int nextRoundAllowedToSend = 1;

        Dictionary<Integer, String> messagesSentInPreviousRounds = new Hashtable<>();

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

            // See if the room is in a real round
            if (round == 1 || round%2 == 0) {
                // Check if my message was already transmitted, i just send "0#0"
                if (messageTransmitted) {
                    sender.send("0#0");
                    System.out.println("Sending message 0#0");
                    continue;
                }

                // Sending message M to the rest of the room if i'm allowed to. If not, i send "0#0"
                if (nextRoundAllowedToSend == round) {
                    sender.send(outputMessage);
                    System.out.println("Sending message " + outputMessage);
                } else {
                    sender.send("0#0");
                    System.out.println("Sending message 0#0");
                }


                // Receive information from the receiver thread in order to know how to proceed in the next rounds
                // Check if there were or not collisions
                // Receive the message from receiver thread
                String messageReceivedFromReceiverThread = receiverThread.recvStr(0);
                System.out.println("Received message from receiver thread = " + messageReceivedFromReceiverThread);

                // If it's equals %, it means that there were no collisions
                if (messageReceivedFromReceiverThread.charAt(0) == '$') {

                    System.out.println("NO COLLISION!");

                    // Take out the first character of the message sent by the receiver thread
                    messageReceivedFromReceiverThread = messageReceivedFromReceiverThread.substring(1);

                    // Check if the message receive from receiver thread equals the message <m> that i sent
                    if (messageReceivedFromReceiverThread.equals("" + outputNumericMessage)) {
                        System.out.println("My message was received!");
                        messageTransmitted = true;
                    }

                }

                // If not, it means that there were a collision
                else {
                    System.out.println("COLLISION!");

                    // Take out the first character of the message sent by the receiver thread
                    messageReceivedFromReceiverThread = messageReceivedFromReceiverThread.substring(1);

                    // If my message is below the average, i send my next message in round 2*<round>
                    if (outputNumericMessage < Integer.parseInt(messageReceivedFromReceiverThread)) {
                        nextRoundAllowedToSend = 2*round;
                    }

                    // If my message is above the average, my message will be retransmitted automatically

                }



            }

            // If not, we are in a virtual round and the message must be calculated automatically
            // If we are in round <round> we must check (<round>-1) and ((<round>-1)/2)
            else {
                String messageSentInRound2K = messagesSentInPreviousRounds.get(round-1);
                String messageSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                System.out.println("Mensajes enviados en rondas 2k y k");
                System.out.println(messageSentInRound2K);
                System.out.println(messageSentInRoundK);

                new BufferedReader(new InputStreamReader(System.in)).readLine(); // <-- Used to stop the while

            }

            round++;

            // new BufferedReader(new InputStreamReader(System.in)).readLine(); // <-- Used to stop the while

        }

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

    }

}
