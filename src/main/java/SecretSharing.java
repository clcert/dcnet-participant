import java.math.BigInteger;
import java.util.Random;

class SecretSharing {

    // Number of shares to split the secret
    private int n;

    SecretSharing(int n) {
        this.n = n;
    }

    BigInteger[] splitSecret(BigInteger secret) {
        int bitLength = secret.bitLength();
        BigInteger[] shares = new BigInteger[this.n];
        BigInteger randomnessAdded = BigInteger.ZERO;
        for (int i = 0; i < shares.length - 1; i++) {
            BigInteger randomValue = new BigInteger(bitLength, new Random()).negate();
            while (randomValue.bitLength() != bitLength)
                randomValue = new BigInteger(bitLength, new Random());
            shares[i] = randomValue;
            randomnessAdded = randomnessAdded.add(randomValue);
        }
        shares[this.n - 1] = secret.subtract(randomnessAdded);
        return shares;
    }

}
