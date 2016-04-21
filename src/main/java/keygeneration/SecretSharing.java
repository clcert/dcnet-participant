package keygeneration;

import dcnet.Room;
import org.zeromq.ZMQ;

import java.math.BigInteger;
import java.util.Random;

public class SecretSharing implements KeyGeneration {

    // Number of shares to split the secret
    private int n;

    // Secret (Random Key to split)
    private BigInteger secret;

    // Other participant nodes round keys (Shares of other participant nodes secret)
    private BigInteger[] otherNodesRandomKeyShares;

    private int nodeIndex;
    private ZMQ.Socket[] repliers, requestors;
    private Room room;

    private BigInteger[] roundRandomKeyShares;

    public SecretSharing(int n, int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        this.n = n;
        this.secret = new BigInteger(room.getQ().bitLength() - 1, new Random());
        while (this.secret.bitLength() != room.getQ().bitLength() - 1)
            this.secret = new BigInteger(room.getQ().bitLength(), new Random());
        this.nodeIndex = nodeIndex;
        this.repliers = repliers;
        this.requestors = requestors;
        this.room = room;
    }

    @Override
    public BigInteger[] generateParticipantNodeRoundKeys() {
        int bitLength = secret.bitLength();
        BigInteger[] shares = new BigInteger[this.n];
        BigInteger randomnessAdded = BigInteger.ZERO;
        for (int i = 0; i < shares.length - 1; i++) {
            BigInteger randomValue = new BigInteger(bitLength, new Random()).negate();
            while (randomValue.bitLength() != bitLength)
                randomValue = new BigInteger(bitLength, new Random());
            shares[i] = randomValue;
            randomnessAdded = randomnessAdded.add(randomValue);
        }
        shares[this.n - 1] = secret.subtract(randomnessAdded);
        this.roundRandomKeyShares = shares;
        return shares;
    }

    @Override
    public BigInteger[] getOtherParticipantNodesRoundKeys() {
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
        return otherNodesRandomKeyShares;
    }

    @Override
    public BigInteger[] getRoundKeys() {
        return this.roundRandomKeyShares;
    }

    @Override
    public BigInteger getParticipantNodeRoundKeyValue() {
        BigInteger result = BigInteger.ZERO;

        for (BigInteger otherNodeRandomKeyShare : otherNodesRandomKeyShares)
            result = result.add(otherNodeRandomKeyShare);

        return result.subtract(this.secret);
    }
}
