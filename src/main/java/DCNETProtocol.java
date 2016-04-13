import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

/**
 *
 */
public class DCNETProtocol {

    /**
     * Usage: ./gradlew run -PappArgs=[{message},{directoryIP},{nonProbabilisticMode}]
     * @param args message, ip address of directory node and non probabilistic mode?
     */
    public static void main(String[] args) {
        // Parse arguments
        String message = args[0];
        String directoryIp = args[1];
        boolean nonProbabilistic = Boolean.parseBoolean(args[2]);

        // Create DirectoryNode object with the IP from arguments
        DirectoryNode directoryNode = new DirectoryNode(directoryIp);

        // Create ParticipantNode object, extracting before the local IP address of the machine where the node is running
        String nodeIp = ParticipantNode.getLocalNetworkIp();
        System.out.println("My IP: " + nodeIp);
        ParticipantNode participantNode = new ParticipantNode(nodeIp);

        // Create empty objects Room, SessionManager and OutputMessage
        Room room = new Room();
        SessionManager sessionManager = new SessionManager();
        OutputMessage outputMessage = new OutputMessage();

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

        // Set parameters to the OutputMessage object: IP address of sender, command type of the message, message itself and room where is going to be send
        // outputMessage.setSenderNodeIp(participantNode.getNodeIp());
        // outputMessage.setMessage(message, room);

        // Set resending ProbabilisticMode to the room: true or false
        room.setNonProbabilisticMode(nonProbabilistic);

        // Create sender socket
        participantNode.createSender(context);

        // Run session with the established parameters
        sessionManager.runSession(nodeIndex, message, room, participantNode, receiverThread);

        // Print total time of execution and how many rounds the session played
        System.out.println("\nTotal Time: " + sessionManager.getExecutionTime() / 1000000000.0 + " seconds");
        System.out.println("Real rounds played: " + sessionManager.getRealRoundsPlayed());

        // Close the threads and destroy the context
        receiverThread.close();
        participantNode.closeSender();
        context.destroy();
        sessionManager.closeRepliersAndRequestorsSockets(nodeIndex, room.getRoomSize());

        // Print all the messages received in this session
        // System.out.println("\nMessages received: ");
        // sessionManager.printMessagesReceived();

    }

}
