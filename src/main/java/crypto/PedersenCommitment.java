package crypto;

import java.math.BigInteger;
import java.util.Random;

/**
 * @author Camilo J. Gómez (camilo@niclabs.cl)
 */
public class PedersenCommitment {


    private BigInteger g, h, q, p;

    /**
     * Constructor with parameters for crypto.PedersenCommitment class
     * @param g generator of group G_q
     * @param h generator of group G_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     */
    public PedersenCommitment(BigInteger g, BigInteger h, BigInteger q, BigInteger p) {
        this.g = g;
        this.h = h;
        this.q = q;
        this.p = p;
    }

    /**
     * Empty constructor for crypto.PedersenCommitment class
     */
    public PedersenCommitment() {}

    /**
     * Generates a random value
     * @return random value in group Z_q
     */
    public BigInteger generateRandom() {
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    /**
     * Calculate a generate a commitment for (secret, random)
     * @param secret message that will be hidden in the commitment
     * @param random random value used to create commitment
     * @return commitment value as c = g^s * h^r (mod p) = (g^s (mod p) * h^r (mod p)) (mod p)
     */
    public BigInteger calculateCommitment(BigInteger secret, BigInteger random) {
        return (this.g.modPow(secret, this.p).multiply(this.h.modPow(random, this.p))).mod(this.p);
    }

    /**
     * Getter for the generator g of the class
     * @return generator g of group G_q
     */
    BigInteger getG() {
        return g;
    }

    /**
     * Getter for the generator h of the class
     * @return generator h of group G_q
     */
    BigInteger getH() {
        return h;
    }
}
