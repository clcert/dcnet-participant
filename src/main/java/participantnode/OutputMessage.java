package participantnode;

import dcnet.Room;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
class OutputMessage {

    private String ip;

    private String participantMessage;
    private String participantMessageWithPadding;

    private BigInteger protocolMessage;
    private BigInteger roundKeyValue;

    private BigInteger participantMessageWithPaddingBigInteger;

    static private int RANDOM_PADDING_LENGTH;

    /**
     *
     */
    OutputMessage() {}

    /**
     *
     * @return message in BigInteger form
     */
    BigInteger getParticipantMessageWithPaddingBigInteger() {
        return participantMessageWithPaddingBigInteger;
    }

    /**
     *
     * @return protocol message in BigInteger form
     */
    BigInteger getProtocolMessage() {
        return protocolMessage;
    }

    /**
     *
     * @param ip ip address of the sender node
     */
    void setSenderNodeIp(String ip) {
        this.ip = ip;
    }

    /**
     *
     * @param l length of the random characters that will be append to the message
     * @return random characters append to message
     */
    private String generateRandomString(int l) {
        String strAllowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sbRandomString = new StringBuilder(l);
        for(int i = 0 ; i < l; i++){
            //get random integer between 0 and string length
            int randomInt = new Random().nextInt(strAllowedCharacters.length());

            //get char from randomInt index from string and append in StringBuilder
            sbRandomString.append( strAllowedCharacters.charAt(randomInt) );
        }
        return sbRandomString.toString();
    }

    /**
     *
     * @param messageWithRandomness message that went through the protocol which has a random string appended
     * @return message without the randomness
     */
    static String getMessageWithoutRandomness(BigInteger messageWithRandomness) throws UnsupportedEncodingException {
        String _a = new String(messageWithRandomness.toByteArray(), "UTF-8");
        return _a.substring(0, _a.length() - RANDOM_PADDING_LENGTH);
    }

    void setParticipantMessage(String participantMessage, Room room) {
        this.participantMessage = participantMessage;
        // Generate random characters to prevent infinite protocol when equal messages collide
        String randomString = generateRandomString(RANDOM_PADDING_LENGTH);

        // this.participantMessageWithPadding = randomString.concat(participantMessage);
        this.participantMessageWithPadding = participantMessage.concat(randomString);
        this.participantMessageWithPaddingBigInteger = new BigInteger(this.participantMessageWithPadding.getBytes());

        // Set to the participantnode.OutputMessage object the actual message that the node wants to communicate (<m>)
        // If the message is 0, the node doesn't want to send any message to the room
        if (participantMessage.equals("0")) {
            this.protocolMessage = BigInteger.ZERO;
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            int a = room.getRoomSize()+1;
            this.protocolMessage = participantMessageWithPaddingBigInteger.multiply(BigInteger.valueOf(a)).add(BigInteger.ONE);
        }
    }

    void setRoundKeyValue(BigInteger roundKeyValue) {
        this.roundKeyValue = roundKeyValue;
        this.protocolMessage = this.protocolMessage.add(this.roundKeyValue);
    }

    void setPaddingLength(int paddingLength) {
        RANDOM_PADDING_LENGTH = paddingLength;
    }
}
