public class OutputMessage {

    String ip;
    private int cmd;
    private int messageProtocol;
    private int messageNumber;

    public OutputMessage(String ip, int cmd, int messageProtocol) {
        this.ip = ip;
        this.cmd = cmd;
        this.messageProtocol = messageProtocol;
    }

    public OutputMessage() {
        this.cmd = 0;
        this.messageProtocol = 0;
    }

    public void setSenderNodeIp(String ip) {
        this.ip = ip;
    }

    public void setMessage(String message, Room room) {
        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        int messageNumber = Integer.parseInt(message);
        this.messageNumber = messageNumber;
        // If the message is 0, the node doesn't want to send any message to the room
        if (messageNumber == 0) {
            this.setMessageProtocol(0);
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            this.setMessageProtocol(messageNumber*(room.getRoomSize()+1) + 1);
        }
    }

    public int getMessageNumber() {
        return this.messageNumber;
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    public int getMessageProtocol() {
        return messageProtocol;
    }

    public void setMessageProtocol(int messageProtocol) {
        this.messageProtocol = messageProtocol;
    }



}
