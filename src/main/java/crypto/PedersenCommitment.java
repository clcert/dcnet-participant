package crypto;

import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
public class PedersenCommitment {


    private BigInteger g, h, q, p;

    /**
     *
     * @param g generator g
     * @param h generator h
     * @param q large prime q
     * @param p large prime p s.t. p = kq + 1
     */
    public PedersenCommitment(BigInteger g, BigInteger h, BigInteger q, BigInteger p) {
        this.g = g;
        this.h = h;
        this.q = q;
        this.p = p;
    }

    /**
     *
     */
    public PedersenCommitment() {}

    /**
     *
     * @return random value in group Z_q
     */
    private BigInteger generateRandom() {
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    /**
     *
     * @param secret message that will be hidden in the commitment
     * @return commitment value as c = g^s h^r (mod p)
     */
    public BigInteger calculateCommitment(BigInteger secret) {
        return this.g.modPow(secret, this.p).multiply(this.h.modPow(generateRandom(), this.p)).mod(this.p);
    }

}
