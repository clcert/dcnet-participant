package dcnet;

/**
 *
 */
public class DirectoryNode {

    private String directoryIp;

    /**
     * Constructor of the class using the ip address of the directory node
     *
     * @param directoryIp ip address of the directory node
     */
    DirectoryNode(String directoryIp) {
        this.directoryIp = directoryIp;
    }

    /**
     * Get the ip address of the directory node
     *
     * @return ip address of the directory node
     */
    public String getDirectoryIp() {
        return directoryIp;
    }

}
