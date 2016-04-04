import java.math.BigInteger;

public class OutputMessage {

    String ip;
    private int cmd;
    private BigInteger messageBigInteger;
    private BigInteger messageBigIntegerProtocol;

    public BigInteger getMessageBigInteger() {
        return messageBigInteger;
    }

    public BigInteger getMessageBigIntegerProtocol() {
        return messageBigIntegerProtocol;
    }

    public OutputMessage(String ip, int cmd, BigInteger messageProtocol) {
        this.ip = ip;
        this.cmd = cmd;
        this.messageBigIntegerProtocol = messageProtocol;
        this.messageBigInteger = BigInteger.ZERO;

    }

    public OutputMessage() {
        this.cmd = 0;
    }

    public void setSenderNodeIp(String ip) {
        this.ip = ip;
    }

    public void setMessage(String message, Room room) {
        this.messageBigInteger = new BigInteger(message.getBytes());

        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        // If the message is 0, the node doesn't want to send any message to the room
        if (message.equals("0")) {
            this.messageBigIntegerProtocol = BigInteger.ZERO;
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            int a = room.getRoomSize()+1;
            this.messageBigIntegerProtocol = messageBigInteger.multiply(BigInteger.valueOf(a)).add(BigInteger.ONE);
        }
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

}
