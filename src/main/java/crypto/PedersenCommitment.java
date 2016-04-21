package crypto;

import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
public class PedersenCommitment {

    // private int messageSize;
    private BigInteger g, h;
    private BigInteger q, p;

    /*public PedersenCommitment(int messageSize) {
        this.messageSize = messageSize;

        // Generate prime q bigger than the message and prime p s.t. q|(p-1)
        this.q = generateQ(messageSize);
        this.p = generateP();

        // Generate generators {g,h} of group G_q
        this.g = findGenerator();
        this.h = findGenerator();
    }*/

    /**
     *
     * @param g generator g
     * @param h generator h
     * @param q generator q
     * @param p generator p
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
        // Generate random in Z_q
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    /**
     *
     * @param secret message that will be hidden in the commitment
     * @return commitment value
     */
    public BigInteger calculateCommitment(BigInteger secret) {
        return this.g.modPow(secret, this.p).multiply(this.h.modPow(generateRandom(), this.p)).mod(this.p);
    }

    /**
     *
     * @param messageSize length of the maximum message possible to send
     * @return large prime q
     *//*
    private BigInteger generateQ(int messageSize) {
        return new BigInteger((messageSize+1)*8, new Random()).nextProbablePrime();
    }

    *//**
     *
     * @return large prime p congruent mod 1 with q
     *//*
    private BigInteger generateP() {
        int i = 1;
        BigInteger p = this.q.multiply(new BigInteger("" + i)).add(BigInteger.ONE);
        while (true) {
            int CERTAINTY = 100;
            if (p.isProbablePrime(CERTAINTY))
                break;
            p = this.q.multiply(new BigInteger("" + i)).add(BigInteger.ONE);
            i++;
        }
        return p;
    }

    *//**
     *
     * @return generator of the group G_q
     *//*
    private BigInteger findGenerator() {
        // Select a random possible <generator> in Z_p
        BigInteger generator = new BigInteger(this.p.bitCount(), new Random()).mod(this.p);
        BigInteger result;
        while (true) {
            // Check that <generator> is not 1 and in Z_p*
            if (!generator.equals(BigInteger.ONE) && generator.gcd(this.p).compareTo(BigInteger.ONE) == 0) {
                // Check that <generator> is in G_q
                result = generator.modPow(this.q, this.p);
                if (result.equals(BigInteger.ONE)) {
                    break;
                }
            }
            // Try with another possible generator
            generator = new BigInteger(this.p.bitCount(), new Random()).mod(this.p);
        }
        return generator;
    }*/

   /* *//**
     *
     * @return generator g
     *//*
    public BigInteger getG() {
        return g;
    }

    *//**
     *
     * @return generator h
     *//*
    public BigInteger getH() {
        return h;
    }

    *//**
     *
     * @return large prime q
     *//*
    public BigInteger getQ() {
        return q;
    }

    *//**
     *
     * @return large prime p
     *//*
    public BigInteger getP() {
        return p;
    }*/

}
