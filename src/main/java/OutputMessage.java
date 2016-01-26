public class OutputMessage {

    private String senderId;
    private int cmd;
    private int message;

    public OutputMessage(String nodeId, int cmd, int message) {
        this.senderId = nodeId;
        this.cmd = cmd;
        this.message = message;
    }

    public OutputMessage() {
        this.senderId = "";
        this.cmd = 0;
        this.message = 0;
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

    public int getMessage() {
        return message;
    }

    public void setMessage(int message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }
}
