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
        String network_ip = (String) args[0];
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Where to find other nodes working
        for (int port = 9000; port < 9000 + dcNetSize; port++) {
            receiver.connect("tcp://" + network_ip + ":" + port);
        }

        // Subscribe to whatever the other nodes say
        receiver.subscribe("".getBytes());

        // Read from other nodes
        while (!Thread.currentThread().isInterrupted()) {
            String inputMessage = receiver.recvStr().trim();
            System.out.println("Message received = " + inputMessage);
        }

        receiver.close();

    }

    // Sender Thread
    public void createNode() throws IOException {
        ZContext context = new ZContext();

        // Run method to find all possible connections in the network
        ZThread.fork(context, new NodeDCNET(this.networkIp, this.name), networkIp);

        // Create socket to receive connections from other nodes
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);

        int nodeIndex;

        // Explore in all the ports to bind my connection
        for (nodeIndex = 0; nodeIndex < dcNetSize; nodeIndex++) {
            try {
                sender.bind("tcp://*:900" + nodeIndex);
            } catch (Exception e) {
                continue;
            }
            System.out.println(name + " binded on port 900" + nodeIndex);
            break;
        }

        ZMQ.Socket[] repliers = null;
        ZMQ.Socket[] requesters = null;

        if (nodeIndex != 0) {
            repliers = new ZMQ.Socket[nodeIndex];
            for (int i = 0; i < repliers.length; i++) {
                repliers[i] = context.createSocket(ZMQ.REP);
                repliers[i].bind("tcp://*:700" + (nodeIndex - 1 + i));
                System.out.println(name + " opened connection on port 700" + (nodeIndex - 1 + i));

                // First synchronization
                repliers[i].recv(0);
                repliers[i].send("", 0);
                System.out.println("Synchronized!");
            }
        }

        if (nodeIndex != dcNetSize - 1) {
            requesters = new ZMQ.Socket[dcNetSize - nodeIndex - 1];
            for (int i = 0; i < requesters.length; i++) {
                requesters[i] = context.createSocket(ZMQ.REQ);
                requesters[i].connect("tcp://*:700" + (nodeIndex*2 + i)); // <-- Check this with examples with more than 3 nodes!
                System.out.println(name + " connect to node listening on port 700" + (nodeIndex*2 + i));

                // First synchronization
                requesters[i].send("".getBytes(), 0);
                requesters[i].recv(0);
                System.out.println("Synchronized!");
            }
        }

        // Write to the other nodes
        while (!Thread.currentThread().isInterrupted()) {
            // Sending first message
            String outputFirstMessage = "" + new Random().nextInt(100);
            sender.send(outputFirstMessage);
            System.out.println("Sent first message to the rest of the DCNET = " + outputFirstMessage);

            // Used to stop the while
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            stdIn.readLine();

        }

        context.destroy();

    }

}
