import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 */
class ParticipantNode {

    private String nodeIp;
    private ZMQ.Socket sender;

    /**
     *
     * @param nodeIp ip address of the participant node
     */
    ParticipantNode(String nodeIp) {
        this.nodeIp = nodeIp;
    }

    /**
     * Get the LAN IP address of the node
     * @return local network ip address of the participant node
     */
    static String getLocalNetworkIp() {
        String networkIp = "";
        InetAddress ip;
        try {
            ip = InetAddress.getLocalHost();
            networkIp = ip.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return networkIp;
    }

    /**
     *
     * @param context context where the zmq sockets need to run
     */
    void createSender(ZContext context) {
        this.sender = context.createSocket(ZMQ.PUB);
        bindSenderPort(this.sender);
    }

    /**
     *
     * @param sender zmq socket which is going to send broadcast messages
     */
    private void bindSenderPort(ZMQ.Socket sender) {
        sender.bind("tcp://*:9000");
    }

    /**
     *
     * @return zmq socket where the broadcast messages are going send through
     */
    ZMQ.Socket getSender() {
        return this.sender;
    }

    /**
     *
     * @return ip address of the participant node
     */
    String getNodeIp() {
        return nodeIp;
    }

    /**
     *
     * @param directoryNode directory node where this participant node is connected to
     * @param room room where this participant node is going to send messages
     * @param context context where the zmq sockets need to run
     */
    void connectToDirectoryNode(DirectoryNode directoryNode, Room room, ZContext context) {
        // Create Directory Subscriber and connect to 5555 port
        ZMQ.Socket directorySubscriber = context.createSocket(ZMQ.SUB);
        directorySubscriber.connect("tcp://" + directoryNode.getDirectoryIp() + ":5555");
        directorySubscriber.subscribe("".getBytes());

        // Create Directory Push and connect to 5554 port
        ZMQ.Socket directoryPush = context.createSocket(ZMQ.PUSH);
        directoryPush.connect("tcp://" + directoryNode.getDirectoryIp() + ":5554");

        // Send my IP to the Directory through the PUSH socket
        directoryPush.send(getNodeIp());

        // Wait message from the Directory node (using the SUB socket) with all the {index,ip} pairs of the room
        String directoryJson = directorySubscriber.recvStr();

        InfoFromDirectory infoFromDirectory = new Gson().fromJson(directoryJson, InfoFromDirectory.class);
        room.setRoomInfoFromDirectory(infoFromDirectory);

        directorySubscriber.close();
        directoryPush.close();

    }

    /**
     *
     */
    void closeSender() {
        this.sender.close();
    }

}
