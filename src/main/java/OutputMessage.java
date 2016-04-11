import java.math.BigInteger;

class OutputMessage {

    private String ip;
    private int cmd;
    private BigInteger messageBigInteger;
    private BigInteger messageBigIntegerProtocol;

    OutputMessage(String ip, int cmd, BigInteger messageProtocol) {
        this.ip = ip;
        this.cmd = cmd;
        this.messageBigIntegerProtocol = messageProtocol;
        this.messageBigInteger = BigInteger.ZERO;

    }

    OutputMessage() {
        this.cmd = 0;
    }

    BigInteger getMessageBigInteger() {
        return messageBigInteger;
    }

    BigInteger getMessageBigIntegerProtocol() {
        return messageBigIntegerProtocol;
    }

    void setSenderNodeIp(String ip) {
        this.ip = ip;
    }

    void setMessage(String message, Room room) {
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

    void setCmd(int cmd) {
        this.cmd = cmd;
    }

}
