/**
 * DirectoryNode is the class that has the ip address of the directory node that is being connected to.
 * @author Camilo J. Gomez
 */
class DirectoryNode {

    private String directoryIp;

    /**
     * Constructor of the class using the ip address of the directory node
     * @param directoryIp ip address of the directory node
     */
    DirectoryNode(String directoryIp) {
        this.directoryIp = directoryIp;
    }

    /**
     * Get the ip address of the directory node
     * @return ip address of the directory node
     */
    String getDirectoryIp() {
        return directoryIp;
    }

}
