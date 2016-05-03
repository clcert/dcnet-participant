package keygeneration;

import dcnet.Room;
import org.zeromq.ZMQ;

import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
public class SecretSharing implements KeyGeneration {

    // Number of shares to split the secret
    private int n;

    // Secret (Random Key to split)
    private BigInteger secret;
    private BigInteger random; //


    // Other participant nodes round keys (Shares of other participant nodes secret)
    private BigInteger[] otherNodesRandomKeyShares;

    private BigInteger[] otherNodesRandomShares; //

    private int nodeIndex;
    private ZMQ.Socket[] repliers, requestors;
    private Room room;

    private BigInteger secretShare;
    private BigInteger secretRandomShare; //

    private BigInteger[] roundRandomKeyShares;

    private BigInteger[] randomShares; //
    private BigInteger roundRandomShare;
    private BigInteger roundKey;

    /**
     *
     * @param n number of shares to split the secret
     * @param nodeIndex index of current participant node
     * @param repliers sockets repliers of current participant node
     * @param requestors sockets requestors of current participant node
     * @param room room where the current participant node is sending messages
     */
    public SecretSharing(int n, int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        this.n = n;
        this.secret = new BigInteger(room.getQ().bitLength() - 1, new Random());
        this.random = new BigInteger(room.getQ().bitLength() - 1, new Random());
        while (this.secret.bitLength() != room.getQ().bitLength() - 1)
            this.secret = new BigInteger(room.getQ().bitLength(), new Random());
        while (this.random.bitLength() != room.getQ().bitLength() - 1) //
            this.random = new BigInteger(room.getQ().bitLength(), new Random()); //
        this.nodeIndex = nodeIndex;
        this.repliers = repliers;
        this.requestors = requestors;
        this.room = room;
    }

    /**
     *
     * @return n-1 shares of the secret of current participant node
     */
    @Override
    public BigInteger[] generateParticipantNodeValues() {
        int bitLength = secret.bitLength();
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
            randomnessAddedForRandom = randomValueForRandom.add(randomValueForRandom); //
        }
        secretShare = secret.subtract(randomnessAdded);
        secretRandomShare = random.subtract(randomnessAddedForRandom); //
        this.roundRandomKeyShares = shares;
        this.randomShares = randomShares; //
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
                replier.send(roundRandomKeyShares[i].toString());
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(roundRandomKeyShares[i].toString());
                // The requestor waits to receive a reply with one of the key shares
                otherNodesRandomKeyShares[i] = new BigInteger(requestor.recvStr());
                i++;
            }
        this.otherNodesRandomKeyShares = otherNodesRandomKeyShares;

        i = 0;
        BigInteger[] otherNodesRandomShares = new BigInteger[room.getRoomSize()-1]; //
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                otherNodesRandomShares[i] = new BigInteger(replier.recvStr()); //
                // When the replier receives the message, replies with one of their key shares
                replier.send(randomShares[i].toString()); //
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(randomShares[i].toString()); //
                // The requestor waits to receive a reply with one of the key shares
                otherNodesRandomShares[i] = new BigInteger(requestor.recvStr()); //
                i++;
            }
        this.otherNodesRandomShares = otherNodesRandomShares; //

        return otherNodesRandomShares;
    }

    /**
     *
     * @return n-1 shares of the secret of current participant node
     */
    @Override
    public BigInteger[] getRoundKeys() {
        // Divide roundKey into n-1 shares
        //return this.roundRandomKeyShares;
        return splitSecret(this.roundKey, this.n-1);
    }

    /**
     *
     * @return sum of all other participant nodes shared with current participant node
     * plus share of current participant node secret didn't share with the room, minus current participant node secret
     */
    @Override
    public BigInteger getParticipantNodeRoundKeyValue() {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger otherNodeRandomKeyShare : otherNodesRandomKeyShares)
            result = result.add(otherNodeRandomKeyShare);
        this.roundKey = result.subtract(this.secret).add(secretShare);

        BigInteger _a = BigInteger.ZERO; //
        for (BigInteger otherNodeShares : otherNodesRandomShares) //
            _a = _a.add(otherNodeShares); //
        this.roundRandomShare = _a.subtract(this.random).add(secretRandomShare); //

        return this.roundKey;
    }

    /**
     *
     * @return shared random values
     */
    @Override
    public BigInteger[] getSharedRandomValues() {
        // Divide roundRandomShare into n-1 shares
        //return randomShares; //
        return splitSecret(this.roundRandomShare, this.n-1);
    }

    /**
     *
     * @param secret value that wants to be shared
     * @param n nu,ber of shares that secret will be separated
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
