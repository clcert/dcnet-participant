package dcnet;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;
import participantnode.ParticipantNode;
import participantnode.Receiver;
import participantnode.SessionManager;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class DCNETProtocol {

    static public void runProtocol(String message, String directoryIp, PrintStream out) {
        // Create dcnet.DirectoryNode object with the IP from arguments
        DirectoryNode directoryNode = new DirectoryNode(directoryIp);

        // Create participantnode.ParticipantNode object, extracting before the local IP address of the machine where the node is running
        String nodeIp = ParticipantNode.getLocalNetworkIp();
        System.out.println("My IP: " + nodeIp);
        ParticipantNode participantNode = new ParticipantNode(nodeIp);

        // Create empty objects dcnet.Room and participantnode.SessionManager
        Room room = new Room();
        SessionManager sessionManager = new SessionManager();

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Connect participantnode.ParticipantNode to dcnet.DirectoryNode and wait response from dcnet.DirectoryNode with the information of the rest of the room
        participantNode.connectToDirectoryNode(directoryNode, room, context);

        // Retrieve nodeIndex of this participantnode.ParticipantNode
        int nodeIndex = room.getNodeIndex(participantNode);

        // Initialize Repliers and Requestors sockets, in order to synchronize the room between rounds
        sessionManager.initializeRepliersArray(nodeIndex, context);
        sessionManager.initializeRequestorsArray(nodeIndex, context, room);

        // Create a thread with the participantnode.Receiver in order to receive the messages from the rest of the room
        ZMQ.Socket receiverThread = ZThread.fork(context, new Receiver(), room);

        // Set resending ProbabilisticMode to the room: true or false
        // room.setNonProbabilisticMode(nonProbabilistic);

        // Create sender socket
        participantNode.createSender(context);

        // Run session with the established parameters
        try {
            sessionManager.runSession(nodeIndex, message, room, participantNode, receiverThread, out);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.exit(0);
        }

        // Print total time of execution and how many rounds the session played
        System.out.println("\nTotal Time: " + sessionManager.getExecutionTime() / 1000000000.0 + " seconds");
        System.out.println("Real rounds played: " + sessionManager.getRealRoundsPlayed());

        // Close the threads and destroy the context
        receiverThread.close();
        participantNode.closeSender();
        context.destroy();
        sessionManager.closeRepliersAndRequestorsSockets(nodeIndex, room.getRoomSize());
    }

}
