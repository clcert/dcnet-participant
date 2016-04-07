import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Room {

    HashMap<Integer, ParticipantNode> directoryMap = new HashMap<>();
    int roomSize;
    boolean nonProbabilisticMode;
    BigInteger g, h, q, p;

    public Room() {}

    public int getRoomSize() {
        return this.roomSize;
    }

    // Rescue index (key) of the given node
    public int getNodeIndex(ParticipantNode node) {
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

    public boolean getNonProbabilisticMode() {
        return this.nonProbabilisticMode;
    }

    public String getNodeIpFromIndex(int i) {
        return directoryMap.get(i).getNodeIp();
    }

    public void setNonProbabilisticMode(boolean nonProbabilisticMode) {
        this.nonProbabilisticMode = nonProbabilisticMode;
    }

    public void setDirectoryMapFromNodesInfo(InfoFromDirectory directoryMapFromNodesInfo) {
        this.g = directoryMapFromNodesInfo.getG();
        this.h = directoryMapFromNodesInfo.getH();
        this.q = directoryMapFromNodesInfo.getQ();
        this.p = directoryMapFromNodesInfo.getP();
        ParticipantNodeInfoFromDirectory[] nodes = directoryMapFromNodesInfo.getNodes();
        this.roomSize = nodes.length;
        for (ParticipantNodeInfoFromDirectory node : nodes)
            this.directoryMap.put(node.index, new ParticipantNode(node.ip));
    }

    public BigInteger getG() {
        return g;
    }

    public BigInteger getH() {
        return h;
    }

    public BigInteger getQ() {
        return q;
    }

    public BigInteger getP() {
        return p;
    }
}
