package dcnet;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;
import participantnode.ParticipantNode;
import participantnode.Receiver;
import participantnode.SessionManager;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

/**
 *
 */
public class DCNETProtocol {

    /**
     *
     * @param message plain text message that the participant node wants to communicate
     * @param directoryIp ip address of the directory node
     * @param out PrintStream where to write the messages that go through the protocol
     */
    static public void runProtocol(String message, String directoryIp, boolean cheaterNode, PrintStream out) throws SocketException {
        // Create DirectoryNode object with the IP from arguments
        DirectoryNode directoryNode = new DirectoryNode(directoryIp);

        // Create ParticipantNode object, extracting before the local IP address of the machine where the node is running
        String nodeIp = ParticipantNode.getLocalNetworkIp();
        System.out.println("Participant IP: " + nodeIp);
        ParticipantNode participantNode = new ParticipantNode(nodeIp);

        // Create empty objects Room and SessionManager
        Room room = new Room();
        SessionManager sessionManager = new SessionManager();

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Connect ParticipantNode to DirectoryNode and wait response from DirectoryNode with the information of the rest of the room
        participantNode.connectToDirectoryNode(directoryNode, room, context);

        // Retrieve nodeIndex of this ParticipantNode
        int nodeIndex = room.getNodeIndex(participantNode);

        // Initialize Repliers and Requestors sockets, in order to synchronize the room between rounds
        sessionManager.initializeRepliersArray(nodeIndex, context);
        sessionManager.initializeRequestorsArray(nodeIndex, context, room);

        // Create a thread with the Receiver in order to receive the messages from the rest of the room
        ZMQ.Socket receiverThread = ZThread.fork(context, new Receiver(), room);

        // Cut message if it's longer than the max length admitted by the room
        if (message.length() > room.getL())
            message = message.substring(0, room.getL());

        // Create sender socket
        participantNode.createSender(context);

        // Run session with the established parameters
        try {
            sessionManager.runSession(nodeIndex, message, cheaterNode, room, participantNode, receiverThread, out);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        // Print total time of execution and how many rounds the session played
        System.out.println("\nTotal Time: " + sessionManager.getExecutionTime() / 1000000000.00 + " seconds");
        System.out.println("Time to get first message: " + sessionManager.getFirstMessageTime() / 1000000000.00 + " seconds");
        System.out.println("Average time per message: " + sessionManager.getAverageTimePerMessage() / 1000000000.00 + " seconds");
        System.out.println("Real rounds played: " + sessionManager.getRealRoundsPlayed());

        // Close the threads and destroy the context
        receiverThread.close();
        participantNode.closeSender();
        context.destroy();
        sessionManager.closeRepliersAndRequestorsSockets(nodeIndex, room.getRoomSize());

    }

}
