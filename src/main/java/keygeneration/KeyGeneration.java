package keygeneration;

import java.math.BigInteger;

/**
 *
 */
public interface KeyGeneration {

    BigInteger[] generateParticipantNodeValues();

    BigInteger[] getOtherParticipantNodesValues();

    BigInteger[] getRoundKeys();

    BigInteger getParticipantNodeRoundKeyValue();

    BigInteger[] getSharedRandomValues();

}
