import dcnet.DCNETProtocol;

import java.io.UnsupportedEncodingException;
import java.net.SocketException;

public class TestInConsole {

    /**
     * Usage: ./gradlew run -PappArgs=[{message},{directoryIP},{cheatingMode}]
     * @param args message and ip address of directory node
     */
    public static void main(String[] args) throws UnsupportedEncodingException, SocketException {
        // Parse arguments
        String message = args[0];
        String directoryIp = args[1];
        boolean cheaterNode = Boolean.parseBoolean(args[2]);

        DCNETProtocol dcnetProtocol = new DCNETProtocol();
        dcnetProtocol.connectToDirectory(directoryIp);

        System.out.println("Participant IP: " + dcnetProtocol.getNodeIp());

        // Print info about the room
        System.out.println("PARTICIPANT NODE " + dcnetProtocol.getNodeIndex() + " of " + dcnetProtocol.getRoomSize());
        if (message.equals("")) {
            System.out.println("\nP_" + dcnetProtocol.getNodeIndex() + " doesn't want to communicate any message\n");
        }
        else
            System.out.println("\nm_" + dcnetProtocol.getNodeIndex() + " = " + message + "\n");


        dcnetProtocol.setMessageToSend(message, cheaterNode);
        dcnetProtocol.runProtocol(System.out);

        System.out.println("\nTotal Time: " + dcnetProtocol.getTotalTime() + " seconds");
        System.out.println("Time to get first message: " + dcnetProtocol.getFirstMessageTime() + " seconds");
        System.out.println("Average time per message: " + dcnetProtocol.getAverageTimePerMessage() + " seconds");
        System.out.println("Real rounds played: " + dcnetProtocol.getNumberOfRealRounds());

    }

}
