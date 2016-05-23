package crypto;

import java.math.BigInteger;

class Commitment {

    private BigInteger g, p;

    Commitment(BigInteger g, BigInteger p) {
        this.g = g;
        this.p = p;
    }

    BigInteger calculateCommitment(BigInteger x) {
        return this.g.modPow(x, this.p);
    }

    BigInteger getG() {
        return g;
    }
}
