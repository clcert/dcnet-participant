package crypto;

import java.math.BigInteger;

public class Commitment {

    BigInteger g, p;

    public Commitment(BigInteger g, BigInteger p) {
        this.g = g;
        this.p = p;
    }

    public BigInteger calculateCommitment(BigInteger x) {
        return this.g.modPow(x, this.p);
    }

    public BigInteger getG() {
        return g;
    }
}
