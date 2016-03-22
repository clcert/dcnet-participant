import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Room {

    HashMap<Integer, ParticipantNode> directoryMap = new HashMap<>();
    int roomSize;
    boolean nonProbabilisticMode;

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

    public void setDirectoryMapFromNodesInfo(NodesInTheRoom directoryMapFromNodesInfo) {
        ParticipantNodeInfoFromDirectory[] nodes = directoryMapFromNodesInfo.getNodes();
        this.roomSize = nodes.length;
        for (int i = 0; i < nodes.length; i++) {
            this.directoryMap.put(nodes[i].index, new ParticipantNode(nodes[i].ip));
        }
    }
}
