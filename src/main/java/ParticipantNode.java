import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.net.InetAddress;
import java.net.UnknownHostException;

class ParticipantNode {

    private String nodeIp;
    private ZMQ.Socket sender;

    ParticipantNode(String nodeIp) {
        this.nodeIp = nodeIp;
    }

    // Get the LAN IP address of the node
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

    void createSender(ZContext context) {
        this.sender = context.createSocket(ZMQ.PUB);
        bindSenderPort(this.sender);
    }

    private void bindSenderPort(ZMQ.Socket sender) {
        sender.bind("tcp://*:9000");
    }

    ZMQ.Socket getSender() {
        return this.sender;
    }

    String getNodeIp() {
        return nodeIp;
    }

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

    void closeSender() {
        this.sender.close();
    }

}
