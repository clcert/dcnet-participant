package dcnet;

import json.ParticipantNodeInfoFromDirectory;
import participantnode.ParticipantNode;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class Room {

    private HashMap<Integer, ParticipantNode> directoryMap = new HashMap<>();
    private int roomSize;
    private boolean nonProbabilisticMode;
    private BigInteger g, h, q, p;
    private int l;
    private int padLength;

    /**
     *
     */
    Room() {}

    /**
     * Rescue index (key) of the given node
     * @param node participant node
     * @return index assigned to node
     */
    int getNodeIndex(ParticipantNode node) {
        Set directorySet = this.directoryMap.entrySet();
        for (Object aDirectorySet : directorySet) {
            Map.Entry mapEntry = (Map.Entry) aDirectorySet;
            int indexKey = (int) mapEntry.getKey();
            ParticipantNode nodeInDirectory = this.directoryMap.get(indexKey);
            if (nodeInDirectory.getNodeIp().equals(node.getNodeIp()))
                return indexKey;
        }
        return 0;
    }

    /**
     *
     * @param i index of the node
     * @return ip address of the node
     */
    public String getNodeIpFromIndex(int i) {
        return directoryMap.get(i).getNodeIp();
    }

    /**
     *
     * @param infoFromDirectory info of nodes
     */
    public void setRoomInfoFromDirectory(InfoFromDirectory infoFromDirectory) {
        this.g = infoFromDirectory.getG();
        this.h = infoFromDirectory.getH();
        this.q = infoFromDirectory.getQ();
        this.p = infoFromDirectory.getP();
        this.l = infoFromDirectory.getL();
        this.padLength = infoFromDirectory.getPadLength();
        this.nonProbabilisticMode = infoFromDirectory.getNonProbabilistic();
        ParticipantNodeInfoFromDirectory[] nodes = infoFromDirectory.getNodes();
        this.roomSize = nodes.length;
        for (ParticipantNodeInfoFromDirectory node : nodes)
            this.directoryMap.put(node.getIndex(), new ParticipantNode(node.getIp()));
    }

    /**
     *
     * @return generator g
     */
    public BigInteger getG() {
        return g;
    }

    /**
     *
     * @return generator h
     */
    public BigInteger getH() {
        return h;
    }

    /**
     *
     * @return large prime q
     */
    public BigInteger getQ() {
        return q;
    }

    /**
     *
     * @return large prime p
     */
    public BigInteger getP() {
        return p;
    }

    /**
     *
     * @return max characters length of a message
     */
    int getL() {
        return l;
    }

    /**
     *
     * @return random padding characters length added to all messages to prevent equal messages
     */
    public int getPadLength() {
        return padLength;
    }

    /**
     *
     * @return true if the room is in non probabilistic mode or false if not
     */
    public boolean getNonProbabilisticMode() {
        return this.nonProbabilisticMode;
    }

    /**
     *
     * @return maximum number of nodes in the room
     */
    public int getRoomSize() {
        return this.roomSize;
    }

    public void setNonProbabilisticMode(boolean nonProbabilisticMode) {
        this.nonProbabilisticMode = nonProbabilisticMode;
    }
}
