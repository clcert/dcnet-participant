import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

class CreateNodes {

    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Usage: java CreateNodes <numberOfNodes>");
            System.exit(0);
        }

        int n = Integer.parseInt(args[0]);

        NodeDCNET[] nodes = new NodeDCNET[n];

        String networkIp = getNetworkIp();

        for (int i = 0; i < n; i++) {
            nodes[i] = new NodeDCNET(networkIp, "Node " + i, "100");
            nodes[i].createNode();
            System.out.println(i);
        }

    }

    private static String getNetworkIp() {

        String networkIp = "";

        InetAddress ip;
        try {

            ip = InetAddress.getLocalHost();
            networkIp = ip.getHostAddress();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return cut_ip(networkIp);
    }

    private static String cut_ip(String ip) {
        String[] numbers = ip.split("\\.");
        return "" + numbers[0] + "." + numbers[1] + "." + numbers[2] + ".";
    }
}
