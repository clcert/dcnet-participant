package dcnet;

import json.ParticipantNodeInfoFromDirectory;

import java.math.BigInteger;

/**
 *
 */
public class InfoFromDirectory {

    private ParticipantNodeInfoFromDirectory[] nodes;
    private BigInteger g, h;
    private BigInteger q, p;
    private int l;
    private boolean nonProbabilistic;
    private int padLength;

    /**
     * @return array with info of nodes connected in the room
     */
    ParticipantNodeInfoFromDirectory[] getNodes() {
        return nodes;
    }

    /**
     * @return generator g
     */
    BigInteger getG() {
        return g;
    }

    /**
     * @return generator h
     */
    BigInteger getH() {
        return h;
    }

    /**
     * @return large prime q
     */
    BigInteger getQ() {
        return q;
    }

    /**
     * @return large prime p
     */
    BigInteger getP() {
        return p;
    }

    /**
     * @return resending mode of the room: true if it's non probabilistic, false if not
     */
    boolean getNonProbabilistic() {
        return nonProbabilistic;
    }

    /**
     * @return max characters length of a message
     */
    int getL() {
        return l;
    }

    /**
     * @return random padding characters length added to all messages to prevent equal messages
     */
    int getPadLength() {
        return padLength;
    }

}
