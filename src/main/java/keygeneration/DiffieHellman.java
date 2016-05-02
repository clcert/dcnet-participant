package keygeneration;

import dcnet.Room;
import org.zeromq.ZMQ;

import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
public class DiffieHellman implements KeyGeneration {

    private final BigInteger g, p;
    private BigInteger[] exponentValuesForKeys;
    private BigInteger[] exponentValuesForRandomShares; //

    private BigInteger[] participantNodeHalves;
    private BigInteger[] otherParticipantNodeHalves;
    private BigInteger[] roundKeys;

    private int nodeIndex;

    private ZMQ.Socket[] repliers, requestors;
    private Room room;

    private BigInteger[] participantNodeSharedRandomValueHalves; //
    private BigInteger[] otherParticipantNodeSharedRandomValueHalves; //
    private BigInteger[] sharedRandomValues; //

    /**
     *
     * @param n number of participant nodes that need to share a key
     * @param g generator g
     * @param p large prime p
     * @param nodeIndex index of current participant node
     * @param repliers sockets repliers of current participant node
     * @param requestors sockets requestors of current participant node
     * @param room room where the current participant node is sending messages
     */
    public DiffieHellman(int n, BigInteger g, BigInteger p, int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        this.g = g;
        this.p = p;
        this.exponentValuesForKeys = new BigInteger[n];
        exponentValuesForRandomShares = new BigInteger[n];
        for (int i = 0; i < exponentValuesForKeys.length; i++) {
            exponentValuesForKeys[i] = new BigInteger(p.bitCount(), new Random());
            exponentValuesForRandomShares[i] = new BigInteger(p.bitCount(), new Random()); //
        }
        this.participantNodeHalves = new BigInteger[n];
        this.nodeIndex = nodeIndex;
        this.repliers = repliers;
        this.requestors = requestors;
        this.room = room;
        this.roundKeys = new BigInteger[n];

        this.sharedRandomValues = new BigInteger[n]; //
        this.participantNodeSharedRandomValueHalves = new BigInteger[n]; //

    }

    /**
     *
     * @return current participant node "halves" of the shared key (g^a)
     */
    @Override
    public BigInteger[] generateParticipantNodeValues() {
        for (int i = 0; i < this.participantNodeHalves.length; i++) {
            this.participantNodeHalves[i] = this.g.modPow(exponentValuesForKeys[i], this.p);
            this.participantNodeSharedRandomValueHalves[i] = this.g.modPow(exponentValuesForRandomShares[i], this.p);
        }
        return this.participantNodeHalves;
    }

    /**
     *
     * @return other participant nodes "halves" of the shared key (g^b)
     */
    @Override
    public BigInteger[] getOtherParticipantNodesValues() {
        int i = 0;
        BigInteger[] otherNodesKeyHalves = new BigInteger[room.getRoomSize()-1];
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                otherNodesKeyHalves[i] = new BigInteger(replier.recvStr());
                // When the replier receives the message, replies with one of their key shares
                replier.send(participantNodeHalves[i].toString());
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(participantNodeHalves[i].toString());
                // The requestor waits to receive a reply with one of the key shares
                otherNodesKeyHalves[i] = new BigInteger(requestor.recvStr());
                i++;
            }
        this.otherParticipantNodeHalves = otherNodesKeyHalves;

        i = 0;
        this.otherParticipantNodeSharedRandomValueHalves = new BigInteger[room.getRoomSize() - 1]; //
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a key share
                this.otherParticipantNodeSharedRandomValueHalves[i] = new BigInteger(replier.recvStr()); //
                // When the replier receives the message, replies with one of their key shares
                replier.send(participantNodeSharedRandomValueHalves[i].toString()); //
                i++;
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a key share
                requestor.send(participantNodeSharedRandomValueHalves[i].toString()); //
                // The requestor waits to receive a reply with one of the key shares
                this.otherParticipantNodeSharedRandomValueHalves[i] = new BigInteger(requestor.recvStr()); //
                i++;
            }

        return otherNodesKeyHalves;
    }

    /**
     *
     * @return sum of all the shared keys of current participant node (where each shared key is g^(ab))
     */
    @Override
    public BigInteger getParticipantNodeRoundKeyValue() {
        int _a = nodeIndex - 1;
        int i;
        for(i = 0; i < _a; i++) {
            roundKeys[i] = otherParticipantNodeHalves[i].modPow(exponentValuesForKeys[i], p).negate();
            sharedRandomValues[i] = otherParticipantNodeSharedRandomValueHalves[i].modPow(exponentValuesForRandomShares[i], p).negate(); //
        }
        for (int j = i; j < roundKeys.length; j++) {
            roundKeys[j] = otherParticipantNodeHalves[j].modPow(exponentValuesForKeys[j], p);
            sharedRandomValues[j] = otherParticipantNodeSharedRandomValueHalves[j].modPow(exponentValuesForRandomShares[j], p); //
        }
        BigInteger roundKeyValue = BigInteger.ZERO;
        for (BigInteger roundKey : roundKeys) {
            roundKeyValue = roundKeyValue.add(roundKey);
        }
        return roundKeyValue;
    }

    /**
     *
     * @return each shared key of current node (g^ab) with all the participant nodes in the room
     */
    @Override
    public BigInteger[] getRoundKeys() {
        return roundKeys;
    }

    /**
     *
     * @return shared random values
     */
    @Override
    public BigInteger[] getSharedRandomValues() {
        return sharedRandomValues;
    }

}
