import java.math.BigInteger;

public class InfoFromDirectory {

    public ParticipantNodeInfoFromDirectory[] getNodes() {
        return nodes;
    }

    ParticipantNodeInfoFromDirectory[] nodes;
    BigInteger g, h;
    BigInteger q, p;

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
