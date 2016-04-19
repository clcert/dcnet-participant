import java.io.UnsupportedEncodingException;

public class TestInConsole {

    /**
     * Usage: ./gradlew run -PappArgs=[{message},{directoryIP}]
     * @param args message and ip address of directory node
     */
    public static void main(String[] args) throws UnsupportedEncodingException {
        // Parse arguments
        String message = args[0];
        String directoryIp = args[1];

        DCNETProtocol.runProtocol(message, directoryIp, System.out);
    }

}
