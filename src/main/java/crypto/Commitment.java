package crypto;

import java.math.BigInteger;
import java.util.Random;

/**
 * @author Camilo J. Gómez (camilo@niclabs.cl)
 */
public class Commitment {

    private BigInteger g, q, p;

    /**
     * Constructor for crypto.Commitment class
     * @param g generator of group G_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     */
    public Commitment(BigInteger g, BigInteger q, BigInteger p) {
        this.g = g;
        this.q = q;
        this.p = p;
    }

    /**
     * Generates a random value
     * @return random value in group Z_q
     */
    BigInteger generateRandom() {
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    /**
     * Calculate a generate a commitment for x
     * @param x value in group Z_q
     * @return commitment c = g^x (mod p)
     */
    public BigInteger calculateCommitment(BigInteger x) {
        return this.g.modPow(x, this.p);
    }

    /**
     * Getter for the generator g of the class
     * @return generator g of group G_q
     */
    BigInteger getG() {
        return g;
    }
}
