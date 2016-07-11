package participantnode;

import dcnet.Room;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
public class OutputMessage {

    private BigInteger plainMessage; // m
    private BigInteger randomPadding; // r*
    private BigInteger plainMessageWithRandomPadding; // m + r*
    private BigInteger finalBit; // b
    private BigInteger protocolMessage; // m + r* + b

    static private int RANDOM_PADDING_LENGTH;

    /**
     *
     */
    OutputMessage() {}

    public BigInteger getPlainMessage() {
        return plainMessage;
    }

    public BigInteger getFinalBit() {
        return finalBit;
    }

    public BigInteger getRandomPadding() {
        return randomPadding;
    }

    /**
     *
     * @return message in BigInteger form
     */
    BigInteger getPlainMessageWithRandomPadding() {
        return plainMessageWithRandomPadding;
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
     * @param l length of the random characters that will be append to the message
     * @return random characters append to message
     */
    private String generateRandomString(int l) {
        String strAllowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789%&"; // 64 characters
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
     * @param messageWithRandomPadding message that went through the protocol which has a random string appended
     * @return message without the randomness
     */
    static String getMessageWithoutRandomPadding(BigInteger messageWithRandomPadding, Room room) throws UnsupportedEncodingException {
        BigInteger nPlusOne = BigInteger.valueOf(room.getRoomSize()+1);
        BigInteger two = BigInteger.valueOf(2);

        BigInteger _a = messageWithRandomPadding.divide(two.pow(RANDOM_PADDING_LENGTH*8).multiply(nPlusOne));
        return new String(_a.toByteArray(), "UTF-8");
    }

    /**
     *
     * @param participantMessage plain text message that participant node wants to communicate
     * @param room room where the current participant node is sending messages
     * @throws UnsupportedEncodingException
     */
    void setParticipantMessage(String participantMessage, Room room) throws UnsupportedEncodingException {
        BigInteger nPlusOne = BigInteger.valueOf(room.getRoomSize()+1);
        BigInteger two = BigInteger.valueOf(2);

        // Generate random characters to prevent infinite protocol when equal messages collide
        String randomString = generateRandomString(RANDOM_PADDING_LENGTH);
        BigInteger randomStringBigInteger = BigInteger.ZERO;
        if (randomString.length() != 0)
            randomStringBigInteger = new BigInteger(randomString.getBytes("UTF-8"));
        randomPadding = randomStringBigInteger;

        // Transform participant message to Big Integer
        BigInteger participantMessageBigInteger = new BigInteger(participantMessage.getBytes("UTF-8"));
        plainMessage = participantMessageBigInteger;

        // Calculate concatenation of participant message and random characters, leaving a gap of log(n+1) bits between them
        this.plainMessageWithRandomPadding = participantMessageBigInteger.multiply(two.pow(RANDOM_PADDING_LENGTH*8).multiply(nPlusOne)).add(randomStringBigInteger);

        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        // If the message is 0, the node doesn't want to send any message to the room
        if (participantMessage.equals("0")) {
            this.protocolMessage = BigInteger.ZERO;
            finalBit = BigInteger.ZERO;
            this.randomPadding = BigInteger.ZERO; //
            this.plainMessage = BigInteger.ZERO; //
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            this.protocolMessage = plainMessageWithRandomPadding.multiply(nPlusOne).add(BigInteger.ONE);
            finalBit = BigInteger.ONE;
        }
    }

    /**
     *
     * @param roundKeyValue round key that needs to be added to protocol message to "hide" the plain text message that wants to be communicated in the room
     */
    void setRoundKeyValue(BigInteger roundKeyValue) {
        this.protocolMessage = this.protocolMessage.add(roundKeyValue);
    }

    /**
     *
     * @param paddingLength characters length of random padding added to messages to prevent equal messages in the protocol
     */
    void setPaddingLength(int paddingLength) {
        RANDOM_PADDING_LENGTH = paddingLength;
    }

}
