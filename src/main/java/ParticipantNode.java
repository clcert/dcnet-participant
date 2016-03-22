import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ParticipantNode {

    String nodeIp;
    String name;
    ZMQ.Socket sender;

    public ParticipantNode(String nodeIp) {
        this.nodeIp = nodeIp;
    }

    public void createSender(ZContext context) {
        this.sender = context.createSocket(ZMQ.PUB);
        bindSenderPort(this.sender);
    }

    private void bindSenderPort(ZMQ.Socket sender) {
        sender.bind("tcp://*:9000");
    }

    public ZMQ.Socket getSender() {
        return this.sender;
    }

    public String getNodeIp() {
        return nodeIp;
    }

    // Get the LAN IP address of the node
    public static String getLocalNetworkIp() {
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

    public void connectToDirectoryNode(DirectoryNode directoryNode, Room room, ZContext context) {
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

        NodesInTheRoom nodesInTheRoom = new Gson().fromJson(directoryJson, NodesInTheRoom.class);
        room.setDirectoryMapFromNodesInfo(nodesInTheRoom);

        directorySubscriber.close();
        directoryPush.close();

    }

    public void closeSender() {
        this.sender.close();
    }
}
