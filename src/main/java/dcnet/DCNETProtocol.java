package dcnet;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;
import participantnode.ParticipantNode;
import participantnode.Receiver;
import participantnode.SessionManager;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

/**
 *
 */
public class DCNETProtocol {

    private int nodeIndex;
    private static Room room;
    private static ParticipantNode participantNode;
    private static ZMQ.Socket receiverThread;
    private static ZContext context;
    String directoryIp;
    String nodeIp;
    private String messageToSend;
    private static SessionManager sessionManager;
    private boolean cheaterNode;
    private double totalTime, firstMessageTime, averageTimePerMessage;
    private int numberOfRealRounds;
    private int roomSize;

    public boolean runProtocol(PrintStream out) throws IOException {
        // Run session with the established parameters
        try {
            sessionManager.runSession(nodeIndex, messageToSend, cheaterNode, room, participantNode, receiverThread, out);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        // Print total time of execution and how many rounds the session played
        this.totalTime = sessionManager.getExecutionTime() / 1000000000.0;
        this.firstMessageTime = sessionManager.getFirstMessageTime() / 1000000000.0;
        this.averageTimePerMessage = sessionManager.getAverageTimePerMessage() / 1000000000.0;
        this.numberOfRealRounds = sessionManager.getRealRoundsPlayed();

        // Close the threads and destroy the context
        receiverThread.close();
        participantNode.closeSender();
        context.destroy();
        sessionManager.closeRepliersAndRequestorsSockets(nodeIndex, room.getRoomSize());

        return true;
    }

    public boolean connectToDirectory(String directoryIp) throws SocketException {
        // Create DirectoryNode object with the IP from arguments
        DirectoryNode directoryNode = new DirectoryNode(directoryIp);

        // Create ParticipantNode object, extracting before the local IP address of the machine where the node is running
        String nodeIp = ParticipantNode.getLocalNetworkIp();
        this.nodeIp = nodeIp;
        ParticipantNode participantNode = new ParticipantNode(nodeIp);

        // Create empty objects Room and SessionManager
        Room room = new Room();
        SessionManager sessionManager = new SessionManager();

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Connect ParticipantNode to DirectoryNode and wait response from DirectoryNode with the information of the rest of the room
        participantNode.connectToDirectoryNode(directoryNode, room, context);

        this.directoryIp = directoryIp;
        this.roomSize = room.getRoomSize();

        // Retrieve nodeIndex of this ParticipantNode
        int nodeIndex = room.getNodeIndex(participantNode);

        // Initialize Repliers and Requestors sockets, in order to synchronize the room between rounds
        sessionManager.initializeRepliersArray(nodeIndex, context);
        sessionManager.initializeRequestorsArray(nodeIndex, context, room);

        // Create a thread with the Receiver in order to receive the messages from the rest of the room
        ZMQ.Socket receiverThread = ZThread.fork(context, new Receiver(), room);

        // Create sender socket
        participantNode.createSender(context);

        DCNETProtocol.sessionManager = sessionManager;
        this.nodeIndex = nodeIndex;
        DCNETProtocol.room = room;
        DCNETProtocol.participantNode = participantNode;
        DCNETProtocol.receiverThread = receiverThread;
        DCNETProtocol.context = context;

        return true;
    }

    public boolean setMessageToSend(String message, boolean cheaterNode) {
        this.messageToSend = message;
        this.cheaterNode = cheaterNode;
        return true;
    }

    public String getNodeIp() {
        return nodeIp;
    }

    public double getTotalTime() {
        return totalTime;
    }

    public double getFirstMessageTime() {
        return firstMessageTime;
    }

    public double getAverageTimePerMessage() {
        return averageTimePerMessage;
    }

    public int getNumberOfRealRounds() {
        return numberOfRealRounds;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

    public int getRoomSize() {
        return nodeIndex;
    }
}
