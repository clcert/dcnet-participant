import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

public class OutputMessage {

    String ip;
    private int cmd;
    //private int messageProtocol;
    //private int messageNumber;

    public BigInteger getMessageBigInteger() {
        return messageBigInteger;
    }

    private BigInteger messageBigInteger;

    public BigInteger getMessageBigIntegerProtocol() {
        return messageBigIntegerProtocol;
    }

    private BigInteger messageBigIntegerProtocol;

    public OutputMessage(String ip, int cmd, int messageProtocol) {
        this.ip = ip;
        this.cmd = cmd;
        // this.messageProtocol = messageProtocol;

    }

    public OutputMessage() {
        this.cmd = 0;
        // this.messageProtocol = 0;
    }

    public void setSenderNodeIp(String ip) {
        this.ip = ip;
    }

    public void setMessage(String message, Room room) {
        this.messageBigInteger = new BigInteger(message.getBytes());

        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        int messageNumber = Integer.parseInt(message);
        //this.messageNumber = messageNumber;
        // If the message is 0, the node doesn't want to send any message to the room
        if (messageNumber == 0) {
            // this.setMessageProtocol(0);
            this.messageBigIntegerProtocol = BigInteger.ZERO;
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            int a = room.getRoomSize()+1;
            // this.setMessageProtocol(messageNumber*(a) + 1);
            this.messageBigIntegerProtocol = messageBigInteger.multiply(BigInteger.valueOf(a)).add(BigInteger.ONE);
        }
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    /*public int getMessageNumber() {
        return this.messageNumber;
    }

    public int getMessageProtocol() {
        return messageProtocol;
    }

    public void setMessageProtocol(int messageProtocol) {
        this.messageProtocol = messageProtocol;
    }*/

}
