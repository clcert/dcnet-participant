package keygeneration;

import java.math.BigInteger;

public interface KeyGeneration {

    BigInteger[] generateParticipantNodeRoundKeys();
    BigInteger[] getOtherParticipantNodesRoundKeys();
    BigInteger getParticipantNodeRoundKeyValue();

}
