package dcnet;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;
import participantnode.ParticipantNode;
import participantnode.Receiver;
import participantnode.SessionManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Observable;

/**
 *
 */
public class DCNETProtocol {

    private int nodeIndex;
    private Room room;
    private ParticipantNode participantNode;
    private ZMQ.Socket receiverThread;
    private ZContext context;
    private String directoryIp;
    private String nodeIp;
    private String messageToSend;
    private SessionManager sessionManager;
    private boolean cheaterNode;
    private double totalTime, firstMessageTime, averageTimePerMessage;
    private int numberOfRealRounds;
    private int roomSize;
    private ArrayList<String> messagesList;
    private int messageMaxLength;
    private ObservableMessageArrived observableMessageArrived;
    private ObservableParticipantsLeft observableParticipantsLeft;
    private double syncTime;

    public DCNETProtocol() {
        messagesList = new ArrayList<>();
        observableMessageArrived = new ObservableMessageArrived("");
        observableParticipantsLeft = new ObservableParticipantsLeft();
    }

    /**
     * Method that runs the DC-NET protocol itself by one participant node
     *
     * @return true if the protocol went ok, false otherwise
     * @throws IOException test
     */
    public boolean runProtocol() throws IOException {
        // Run session with the established parameters
        try {
            sessionManager.runSession(nodeIndex, messageToSend, cheaterNode, room, participantNode, receiverThread, messagesList, observableMessageArrived);
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
        this.syncTime = sessionManager.getTotalSyncTime() / 1000000000.0;

        // Close the threads and destroy the context
        receiverThread.close();
        participantNode.closeSender();
        context.destroy();
        sessionManager.closeRepliersAndRequestorsSockets(nodeIndex, room.getRoomSize());

        return true;
    }

    /**
     * Connect participant node to directory node, sending own IP address and receiving IP addresses of all other
     * participant nodes in the room
     *
     * @param directoryIp directory node IP address
     * @return true if the participant node connected to directory node, false otherwise
     * @throws SocketException test
     */
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
        participantNode.connectToDirectoryNode(directoryNode, room, context, observableParticipantsLeft);

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

        this.sessionManager = sessionManager;
        this.nodeIndex = nodeIndex;
        this.room = room;
        this.participantNode = participantNode;
        this.receiverThread = receiverThread;
        this.context = context;
        messageMaxLength = room.getL();

        return true;
    }

    /**
     * Set the message and mode that the participant wants to communicate to the rest of the room
     *
     * @param message     message that the participants wants to communicate
     * @param cheaterNode true if the participant won't send the message in the correct round, false otherwise
     */
    public void setMessageToSend(String message, boolean cheaterNode) {
        this.messageToSend = message;
        this.cheaterNode = cheaterNode;
    }


    public String getNodeIp() {
        return nodeIp;
    }

    public double getSyncTime() {
        return syncTime;
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
        return roomSize;
    }

    public int getMessageMaxLength() {
        return messageMaxLength;
    }

    public ArrayList<String> getMessagesList() {
        return messagesList;
    }

    public ObservableMessageArrived getObservableMessageArrived() {
        return observableMessageArrived;
    }

    public ObservableParticipantsLeft getObservableParticipantsLeft() {
        return observableParticipantsLeft;
    }

    public class ObservableMessageArrived extends Observable {
        private String messageArrived = "";

        public ObservableMessageArrived(String messagesArrived) {
            this.messageArrived = messagesArrived;
        }

        public void setValue(String messageArrived) {
            this.messageArrived = messageArrived;
            setChanged();
            notifyObservers();
        }

        public String getValue() {
            return this.messageArrived;
        }
    }

    public class ObservableParticipantsLeft extends Observable {
        private int participantsLeft;

        public ObservableParticipantsLeft() {
        }

        public void setValue(int participantsLeft) {
            this.participantsLeft = participantsLeft;
            setChanged();
            notifyObservers();
        }

        public int getValue() {
            return this.participantsLeft;
        }

    }

}
