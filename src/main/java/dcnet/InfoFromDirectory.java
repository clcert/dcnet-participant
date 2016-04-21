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

    int getL() {
        return l;
    }

    int getPadLength() {
        return padLength;
    }

    /**
     *
     * @return array with info of nodes connected in the room
     */
    ParticipantNodeInfoFromDirectory[] getNodes() {
        return nodes;
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

    boolean getNonProbabilistic() {
        return nonProbabilistic;
    }

}