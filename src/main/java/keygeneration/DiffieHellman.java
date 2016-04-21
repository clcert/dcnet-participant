package keygeneration;

import dcnet.Room;
import org.zeromq.ZMQ;

import java.math.BigInteger;
import java.util.Random;

public class DiffieHellman implements KeyGeneration {

    private final BigInteger g, p;
    private BigInteger[] exponentValues;
    private BigInteger[] participantNodeHalfs;
    private BigInteger[] otherParticipantNodeHalfs;
    private BigInteger[] roundKeys;

    private int nodeIndex;

    private ZMQ.Socket[] repliers, requestors;
    private Room room;

    public DiffieHellman(int n, BigInteger g, BigInteger p, int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        this.g = g;
        this.p = p;
        this.exponentValues = new BigInteger[n-1];
        for (int i = 0; i < exponentValues.length; i++) {
            exponentValues[i] = new BigInteger(p.bitCount(), new Random());
        }
        this.participantNodeHalfs = new BigInteger[n-1];
        this.nodeIndex = nodeIndex;
        this.repliers = repliers;
        this.requestors = requestors;
        this.room = room;
        this.roundKeys = new BigInteger[n-1];
    }

    @Override
    public BigInteger[] generateParticipantNodeRoundKeys() {
        for (int i = 0; i < this.participantNodeHalfs.length; i++) {
            this.participantNodeHalfs[i] = this.g.modPow(exponentValues[i], this.p);
        }
        return this.participantNodeHalfs;
    }

    @Override
    public BigInteger[] getOtherParticipantNodesRoundKeys() {
        int i = 0;
        BigInteger[] otherNodesKeyHalfs = new BigInteger[room.getRoomSize()-1];
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                otherNodesKeyHalfs[i] = new BigInteger(replier.recvStr());
                // When the replier receives the message, replies with one of their key shares
                replier.send(participantNodeHalfs[i].toString());
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(participantNodeHalfs[i].toString());
                // The requestor waits to receive a reply with one of the key shares
                otherNodesKeyHalfs[i] = new BigInteger(requestor.recvStr());
                i++;
            }
        this.otherParticipantNodeHalfs = otherNodesKeyHalfs;
        return otherNodesKeyHalfs;
    }

    @Override
    public BigInteger getParticipantNodeRoundKeyValue() {
        int _a = nodeIndex - 1;
        int i;
        for(i = 0; i < _a; i++)
            roundKeys[i] = participantNodeHalfs[i].modPow(otherParticipantNodeHalfs[i], p).negate();
        for (int j = i; j < roundKeys.length; j++)
            roundKeys[j] = participantNodeHalfs[j].modPow(otherParticipantNodeHalfs[j], p);
        BigInteger roundKeyValue = BigInteger.ZERO;
        for (BigInteger roundKey : roundKeys) {
            roundKeyValue = roundKeyValue.add(roundKey);
        }
        return roundKeyValue;
    }

    @Override
    public BigInteger[] getRoundKeys() {
        return roundKeys;
    }

}
