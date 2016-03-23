import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

public class DCNETProtocol {

    // Usage: ./gradlew run -PappArgs=[<message>,<directoryIP>,<probabilisticMode>]
    public static void main(String[] args) {
        String message = args[0];
        String directoryIp = args[1];
        boolean nonProbabilistic = Boolean.parseBoolean(args[2]);

        DirectoryNode directoryNode = new DirectoryNode(directoryIp);

        String nodeIp = ParticipantNode.getLocalNetworkIp();
        System.out.println("My IP: " + nodeIp);
        ParticipantNode participantNode = new ParticipantNode(nodeIp);

        Room room = new Room();
        SessionManager sessionManager = new SessionManager();
        OutputMessage outputMessage = new OutputMessage();

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        participantNode.connectToDirectoryNode(directoryNode, room, context);

        int nodeIndex = room.getNodeIndex(participantNode);

        // Print info about the room
        System.out.println("Number of nodes: " + room.getRoomSize());
        System.out.println("My index is: " + nodeIndex);

        sessionManager.initializeRepliersArray(nodeIndex, context);
        sessionManager.initializeRequestorsArray(nodeIndex, context, room);

        ZMQ.Socket receiverThread = ZThread.fork(context, new Receiver(), room);

        outputMessage.setSenderNodeIp(participantNode.getNodeIp());
        outputMessage.setCmd(1);
        outputMessage.setMessage(message, room);

        String outputMessageJson = new Gson().toJson(outputMessage);

        room.setNonProbabilisticMode(nonProbabilistic);

        // Print message to send
        System.out.println("\nm_" + nodeIndex + " = " + message + "\n");

        participantNode.createSender(context);

        // Measure execution time (real time)
        long t1 = System.nanoTime();

        sessionManager.runSession(nodeIndex, outputMessage, room, participantNode, receiverThread, outputMessageJson);

        long t2 = System.nanoTime();

        // Calculate total time of execution and print it
        long total_time = t2-t1;
        System.out.println("Total Time: " + total_time/1000000000.0 + " seconds");
        System.out.println("Real rounds played: " + sessionManager.getRealRoundsPlayed());

        receiverThread.close();
        participantNode.closeSender();
        context.destroy();

        // Print all the messages received in this session
        System.out.println("\nMessages received: ");
        sessionManager.getMessagesReceived().forEach(System.out::println);

    }

}
