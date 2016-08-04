package crypto;

import java.math.BigInteger;
import java.util.Random;

/**
 * Class that manages regular commitments \((c = g^x \pmod{p})\) operations
 *
 * @author Camilo J. Gómez (camilo@niclabs.cl)
 */
public class Commitment {

    /**
     * Generator of group \(G_q\)
     */
    private BigInteger g;

    /**
     * Large prime
     */
    private BigInteger q;

    /**
     * Large prime s.t. \(p = kq + 1\)
     */
    private BigInteger p;

    /**
     * Constructor for crypto.Commitment class
     *
     * @param g generator of group \(G_q\)
     * @param q large prime
     * @param p large prime s.t. \(p = kq + 1\)
     */
    public Commitment(BigInteger g, BigInteger q, BigInteger p) {
        this.g = g;
        this.q = q;
        this.p = p;
    }

    /**
     * Generates a random value
     *
     * @return random value in group \(\mathbb{Z}_q\)
     */
    BigInteger generateRandom() {
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    /**
     * Calculate and generate a commitment for \(x\)
     *
     * @param x value in group \(\mathbb{Z}_q\)
     * @return commitment \(c = g^x \pmod{p}\)
     */
    public BigInteger calculateCommitment(BigInteger x) {
        return this.g.modPow(x, this.p);
    }

    /**
     * Getter for the generator \(g\) of the class
     *
     * @return generator \(g\) of group \(G_q\)
     */
    BigInteger getG() {
        return g;
    }

}