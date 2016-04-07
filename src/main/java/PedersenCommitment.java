import java.math.BigInteger;
import java.util.Random;

public class PedersenCommitment {

    final int CERTAINTY = 100;
    int messageSize;

    private BigInteger g, h;
    private BigInteger q, p;

    public PedersenCommitment(int messageSize) {
        this.messageSize = messageSize;

        // Generate prime q bigger than the message and prime p s.t. q|(p-1)
        this.q = generateQ(messageSize);
        this.p = generateP();

        // Generate generators {g,h} of group G_q
        this.g = findGenerator();
        this.h = findGenerator();
    }

    public PedersenCommitment(BigInteger g, BigInteger h, BigInteger q, BigInteger p) {
        this.g = g;
        this.h = h;
        this.q = q;
        this.p = p;
    }

    public BigInteger generateQ(int messageSize) {
        return new BigInteger((messageSize+1)*8, new Random()).nextProbablePrime();
    }

    private BigInteger generateP() {
        int i = 1;
        BigInteger p = this.q.multiply(new BigInteger("" + i)).add(BigInteger.ONE);
        while (true) {
            if (p.isProbablePrime(CERTAINTY))
                break;
            p = this.q.multiply(new BigInteger("" + i)).add(BigInteger.ONE);
            i++;
        }
        return p;
    }

    public BigInteger findGenerator() {
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
    }

    private BigInteger generateRandom() {
        // Generate random in Z_q
        BigInteger random = new BigInteger(this.q.bitCount(), new Random());
        return random.mod(this.q);
    }

    public BigInteger calculateCommitment(BigInteger secret) {
        return this.g.modPow(secret, this.p).multiply(this.h.modPow(generateRandom(), this.p)).mod(this.p);
        // return myPow(this.g, secret).multiply(myPow(this.h,generateRandom()));
    }

    public BigInteger getG() {
        return g;
    }

    public BigInteger getH() {
        return h;
    }

    public BigInteger getQ() {
        return q;
    }

    public BigInteger getP() {
        return p;
    }

    private BigInteger myPow(BigInteger base, BigInteger exponent) {
        BigInteger result = BigInteger.ONE;
        while (exponent.signum() > 0) {
            if (exponent.testBit(0)) result = result.multiply(base);
            base = base.multiply(base);
            exponent = exponent.shiftRight(1);
        }
        return result;
    }

}
