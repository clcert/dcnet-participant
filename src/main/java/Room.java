import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
class Room {

    private HashMap<Integer, ParticipantNode> directoryMap = new HashMap<>();
    private int roomSize;
    private boolean nonProbabilisticMode;
    private BigInteger g, h, q, p;
    private int l;

    int getPadLength() {
        return padLength;
    }

    private int padLength;

    /**
     *
     */
    Room() {}

    /**
     *
     * @return maximum number of nodes in the room
     */
    int getRoomSize() {
        return this.roomSize;
    }

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
     * @return true if the room is in non probabilistic mode or false if not
     */
    boolean getNonProbabilisticMode() {
        return this.nonProbabilisticMode;
    }

    /**
     *
     * @param i index of the node
     * @return ip address of the node
     */
    String getNodeIpFromIndex(int i) {
        return directoryMap.get(i).getNodeIp();
    }

    /**
     *
     * @param infoFromDirectory info of nodes
     */
    void setRoomInfoFromDirectory(InfoFromDirectory infoFromDirectory) {
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
            this.directoryMap.put(node.index, new ParticipantNode(node.ip));
    }

    /**
     *
     * @return generator g
     */
    BigInteger getG() {
        return g;
    }

    /**
     *
     * @return generator h
     */
    BigInteger getH() {
        return h;
    }

    /**
     *
     * @return large prime q
     */
    BigInteger getQ() {
        return q;
    }

    /**
     *
     * @return large prime p
     */
    BigInteger getP() {
        return p;
    }

}
