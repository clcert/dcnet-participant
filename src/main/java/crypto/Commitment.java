package crypto;

import java.math.BigInteger;
import java.util.Random;

public class Commitment {

    private BigInteger g, q, p;

    public Commitment(BigInteger g, BigInteger q, BigInteger p) {
        this.g = g;
        this.q = q;
        this.p = p;
    }

    /**
     *
     * @return random value in group Z_q
     */
    public BigInteger generateRandom() {
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    public BigInteger calculateCommitment(BigInteger x) {
        return this.g.modPow(x, this.p);
    }

    BigInteger getG() {
        return g;
    }
}
