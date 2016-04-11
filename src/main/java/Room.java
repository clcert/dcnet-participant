import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class Room {

    private HashMap<Integer, ParticipantNode> directoryMap = new HashMap<>();
    private int roomSize;
    private boolean nonProbabilisticMode;
    private BigInteger g, h, q, p;

    Room() {}

    int getRoomSize() {
        return this.roomSize;
    }

    // Rescue index (key) of the given node
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

    boolean getNonProbabilisticMode() {
        return this.nonProbabilisticMode;
    }

    void setNonProbabilisticMode(boolean nonProbabilisticMode) {
        this.nonProbabilisticMode = nonProbabilisticMode;
    }

    String getNodeIpFromIndex(int i) {
        return directoryMap.get(i).getNodeIp();
    }

    void setRoomInfoFromDirectory(InfoFromDirectory infoFromDirectory) {
        this.g = infoFromDirectory.getG();
        this.h = infoFromDirectory.getH();
        this.q = infoFromDirectory.getQ();
        this.p = infoFromDirectory.getP();
        ParticipantNodeInfoFromDirectory[] nodes = infoFromDirectory.getNodes();
        this.roomSize = nodes.length;
        for (ParticipantNodeInfoFromDirectory node : nodes)
            this.directoryMap.put(node.index, new ParticipantNode(node.ip));
    }

    BigInteger getG() {
        return g;
    }

    BigInteger getH() {
        return h;
    }

    BigInteger getQ() {
        return q;
    }

    BigInteger getP() {
        return p;
    }

}
