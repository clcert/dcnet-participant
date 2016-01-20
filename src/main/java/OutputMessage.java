public class OutputMessage {

    private String senderId;
    private int cmd;
    private String message;

    public OutputMessage(String nodeId, int cmd, String message) {
        this.senderId = nodeId;
        this.cmd = cmd;
        this.message = message;
    }

    public OutputMessage() {
        this.senderId = "";
        this.cmd = 0;
        this.message = "";
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public int getCmd() {
        return cmd;
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderId() {

        return senderId;
    }
}
