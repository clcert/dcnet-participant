package keygeneration;

import dcnet.Room;
import org.zeromq.ZMQ;

import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
public class SecretSharing implements KeyGeneration {

    // Number of parts to split the secretKey
    private int n;

    // Secret Key
    private BigInteger secretKey;
    // Secret Random
    private BigInteger secretRandom;

    // Other Participant Nodes shares of their secret key
    private BigInteger[] otherNodesKeyShares;
    // Other Participant Nodes shares of their secret random
    private BigInteger[] otherNodesRandomShares;

    private int nodeIndex;
    private ZMQ.Socket[] repliers, requestors;
    private Room room;

    // Part of secret key didn't shared with any participant node
    private BigInteger privateSecretKeyShare;
    // Part of secret random didn't shared with any participant node
    private BigInteger privateSecretRandomShare;

    // Parts (shares) of secretKey
    private BigInteger[] secretKeyShares;
    // Parts (shares) of secretRandom
    private BigInteger[] secretRandomShares;

    // Resulting round key value
    private BigInteger roundKey;
    // Resulting round random value
    private BigInteger roundRandom;

    /**
     *
     * @param n number of shares to split the secretKey
     * @param nodeIndex index of current participant node
     * @param repliers sockets repliers of current participant node
     * @param requestors sockets requestors of current participant node
     * @param room room where the current participant node is sending messages
     */
    public SecretSharing(int n, int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        this.n = n;
        this.secretKey = new BigInteger(room.getQ().bitLength() - 1, new Random());
        this.secretRandom = new BigInteger(room.getQ().bitLength() - 1, new Random());
        while (this.secretKey.bitLength() != room.getQ().bitLength() - 1)
            this.secretKey = new BigInteger(room.getQ().bitLength(), new Random());
        while (this.secretRandom.bitLength() != room.getQ().bitLength() - 1) //
            this.secretRandom = new BigInteger(room.getQ().bitLength(), new Random()); //
        this.nodeIndex = nodeIndex;
        this.repliers = repliers;
        this.requestors = requestors;
        this.room = room;
    }

    /**
     *
     * @return n-1 shares of the secretKey of current participant node
     */
    @Override
    public BigInteger[] generateParticipantNodeValues() {
        int bitLength = secretKey.bitLength();
        BigInteger[] shares = new BigInteger[this.n - 1];
        BigInteger[] randomShares = new BigInteger[this.n - 1]; //
        BigInteger randomnessAdded = BigInteger.ZERO;
        BigInteger randomnessAddedForRandom = BigInteger.ZERO; //
        for (int i = 0; i < shares.length; i++) {
            BigInteger randomValue = new BigInteger(bitLength, new Random()).negate();
            BigInteger randomValueForRandom = new BigInteger(bitLength, new Random()).negate(); //
            while (randomValue.bitLength() != bitLength)
                randomValue = new BigInteger(bitLength, new Random());
            while (randomValueForRandom.bitLength() != bitLength)
                randomValueForRandom = new BigInteger(bitLength, new Random());
            shares[i] = randomValue;
            randomShares[i] = randomValueForRandom; //
            randomnessAdded = randomnessAdded.add(randomValue);
            randomnessAddedForRandom = randomnessAddedForRandom.add(randomValueForRandom); //
        }
        privateSecretKeyShare = secretKey.subtract(randomnessAdded);
        privateSecretRandomShare = secretRandom.subtract(randomnessAddedForRandom); //
        this.secretKeyShares = shares;
        this.secretRandomShares = randomShares; //
        return shares;
    }

    /**
     *
     * @return 1 share of each n-1 other participant nodes secrets
     */
    @Override
    public BigInteger[] getOtherParticipantNodesValues() {
        int i = 0;
        BigInteger[] otherNodesRandomKeyShares = new BigInteger[room.getRoomSize()-1];
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                otherNodesRandomKeyShares[i] = new BigInteger(replier.recvStr());
                // When the replier receives the message, replies with one of their key shares
                replier.send(secretKeyShares[i].toString());
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(secretKeyShares[i].toString());
                // The requestor waits to receive a reply with one of the key shares
                otherNodesRandomKeyShares[i] = new BigInteger(requestor.recvStr());
                i++;
            }
        this.otherNodesKeyShares = otherNodesRandomKeyShares;

        i = 0;
        BigInteger[] otherNodesRandomShares = new BigInteger[room.getRoomSize()-1]; //
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                otherNodesRandomShares[i] = new BigInteger(replier.recvStr()); //
                // When the replier receives the message, replies with one of their key shares
                replier.send(secretRandomShares[i].toString()); //
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(secretRandomShares[i].toString()); //
                // The requestor waits to receive a reply with one of the key shares
                otherNodesRandomShares[i] = new BigInteger(requestor.recvStr()); //
                i++;
            }
        this.otherNodesRandomShares = otherNodesRandomShares; //

        return otherNodesRandomShares;
    }

    /**
     *
     * @return n-1 shares of the secretKey of current participant node
     */
    @Override
    public BigInteger[] getRoundKeys() {
        // Divide roundKey into n-1 shares
        //return this.secretKeyShares;
        return splitSecret(this.roundKey, this.n-1);
    }

    /**
     *
     * @return sum of all other participant nodes shared with current participant node
     * plus share of current participant node secretKey didn't share with the room, minus current participant node secretKey
     */
    @Override
    public BigInteger getParticipantNodeRoundKeyValue() {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger otherNodeRandomKeyShare : otherNodesKeyShares)
            result = result.add(otherNodeRandomKeyShare);
        this.roundKey = result.subtract(this.secretKey).add(privateSecretKeyShare);

        BigInteger _a = BigInteger.ZERO; //
        for (BigInteger otherNodeShares : otherNodesRandomShares) //
            _a = _a.add(otherNodeShares); //
        this.roundRandom = _a.subtract(this.secretRandom).add(privateSecretRandomShare); //

        return this.roundKey;
    }

    /**
     *
     * @return shared secretRandom values
     */
    @Override
    public BigInteger[] getSharedRandomValues() {
        // Divide roundRandom into n-1 shares
        //return secretRandomShares; //
        return splitSecret(this.roundRandom, this.n-1);
    }

    /**
     *
     * @param secret value that wants to be shared
     * @param n nu,ber of shares that secretKey will be separated
     * @return array with all the n shares
     */
    private BigInteger[] splitSecret(BigInteger secret, int n) {
        int bitLength = secret.bitLength();
        BigInteger[] shares = new BigInteger[n];
        BigInteger randomnessAdded = BigInteger.ZERO;
        for (int i = 0; i < shares.length - 1; i++) {
            BigInteger randomValue = new BigInteger(bitLength, new Random()).negate();
            while (randomValue.bitLength() != bitLength)
                randomValue = new BigInteger(bitLength, new Random());
            shares[i] = randomValue;
            randomnessAdded = randomnessAdded.add(randomValue);
        }
        shares[n - 1] = secret.subtract(randomnessAdded);
        return shares;
    }

}
