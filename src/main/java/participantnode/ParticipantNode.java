package participantnode;

import com.google.gson.Gson;
import dcnet.DirectoryNode;
import dcnet.InfoFromDirectory;
import dcnet.Room;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 *
 */
public class ParticipantNode {

    private String nodeIp;
    private ZMQ.Socket sender;

    /**
     *
     * @param nodeIp ip address of the participant node
     */
    public ParticipantNode(String nodeIp) {
        this.nodeIp = nodeIp;
    }

    /**
     * Get the LAN IP address of the node
     * @return local network ip address of the participant node
     */
    static public String getLocalNetworkIp() throws SocketException {
        /*String networkIp = "";
        InetAddress ip;
        try {
            ip = InetAddress.getLocalHost();
            networkIp = ip.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return networkIp;*/
        String ip = "";
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        while(e.hasMoreElements()) {
            NetworkInterface n = (NetworkInterface) e.nextElement();
            if (!n.getDisplayName().contains("docker")) {
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (!i.isLinkLocalAddress() && !i.isLoopbackAddress()) {
                        ip =  i.getHostAddress();
                    }
                }
            }
        }
        return ip;
    }

    /**
     *
     * @param context context where the zmq sockets need to run
     */
    public void createSender(ZContext context) {
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
    public String getNodeIp() {
        return nodeIp;
    }

    /**
     *
     * @param directoryNode directory node where this participant node is connected to
     * @param room room where this participant node is going to send messages
     * @param context context where the zmq sockets need to run
     */
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

        InfoFromDirectory infoFromDirectory = new Gson().fromJson(directoryJson, InfoFromDirectory.class);
        room.setRoomInfoFromDirectory(infoFromDirectory);

        directorySubscriber.close();
        directoryPush.close();

    }

    /**
     *
     */
    public void closeSender() {
        this.sender.close();
    }

}
