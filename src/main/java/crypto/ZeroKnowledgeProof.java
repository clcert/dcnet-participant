package crypto;

import json.ProofOfKnowledge;
import json.ProofOfKnowledgeOR;
import json.ProofOfKnowledgePedersen;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 */
public class ZeroKnowledgeProof {

    private final int nodeIndex;
    private PedersenCommitment pedersenCommitment;
    private BigInteger p, q;

    /**
     *
     * @param g generator g
     * @param h generator h
     * @param q large prime q
     * @param p large prime p
     * @param nodeIndex index of current participant node
     */
    public ZeroKnowledgeProof(BigInteger g, BigInteger h, BigInteger q, BigInteger p, int nodeIndex) {
        this.pedersenCommitment = new PedersenCommitment(g, h, q, p);
        this.p = p;
        this.q = q;
        this.nodeIndex = nodeIndex;
    }

    /**
     *
     * @param x secret that current node wants to commit to
     * @param r random value needed for commitment
     * @return proof of knowledge that participant node knows x in c = g^x h^r
     */
    public ProofOfKnowledgePedersen generateProofOfKnowledgePedersen(BigInteger c, BigInteger x, BigInteger r) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // BigInteger c = this.pedersenCommitment.calculateCommitment(x, r); // c = g^x h^r (mod p)

        BigInteger y = this.pedersenCommitment.generateRandom(); // y random
        BigInteger s = this.pedersenCommitment.generateRandom(); // s random

        BigInteger d = this.pedersenCommitment.calculateCommitment(y, s); // d = g^y h^s (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = d.toString().concat(
                this.pedersenCommitment.getG().toString()).concat(
                this.pedersenCommitment.getH().toString()).concat(
                c.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger e = new BigInteger(hashOnPublicValues); // e = H( d || g || h || c || nodeIndex )

        BigInteger u = e.multiply(x).add(y); // u = e*x + y
        BigInteger v = e.multiply(r).add(s); // v = e*r + s

        return new ProofOfKnowledgePedersen(d, u, v, nodeIndex);

    }

    /**
     *
     * @param proof proof of knowledge that participant node knows x in commitment inside
     * @return true if the verifying process is succeed, false otherwise
     */
    public boolean verifyProofOfKnowledgePedersen(ProofOfKnowledgePedersen proof, BigInteger commitment) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BigInteger _a = this.pedersenCommitment.calculateCommitment(proof.getU(), proof.getV()); // _a = g^u h^v (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = proof.getD().toString().concat(
                                   this.pedersenCommitment.getG().toString()).concat(
                                   this.pedersenCommitment.getH().toString()).concat(
                                   commitment.toString()).concat(
                                   "" + proof.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        BigInteger e = new BigInteger(md.digest());

        BigInteger _b = proof.getD().mod(this.p).multiply(commitment.modPow(e , this.p)).mod(this.p); // _b = (d (mod p) * (c^e (mod p)) (mod p) = d * c^e (mod p)
        return _a.equals(_b);
    }

    /**
     *
     * @param x secret that current node wants to commit to
     * @return proof of knowledge that participant node knows x in y = g^x
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public ProofOfKnowledge generateProofOfKnowledge(BigInteger x) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(this.pedersenCommitment.getH(), this.p); // Set generator h of commitments
        BigInteger y = commitment.calculateCommitment(x); // y = h^x (mod p)
        BigInteger r = this.pedersenCommitment.generateRandom(); // r random
        BigInteger z = commitment.calculateCommitment(r); // z = h^x (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z.toString().concat(
                commitment.getG().toString()).concat(
                y.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues); // b = H( z || h || y || nodeIndex )

        BigInteger a = r.add(b.multiply(x)); // a = r + b*x

        return new ProofOfKnowledge(commitment.getG(), z, a, nodeIndex);

    }

    /**
     *
     * @param proof proof of knowledge that participant node knows x in commitment y
     * @param y y = h^x
     * @return true if the verifying process is succeed, false otherwise
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public boolean verifyProofOfKnowledge(ProofOfKnowledge proof, BigInteger y) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(this.pedersenCommitment.getH(), this.p); // Set generator h of commitments
        BigInteger _a = commitment.calculateCommitment(proof.getA()); // _a = g^a (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = proof.getZ().toString().concat(
                commitment.getG().toString()).concat(
                y.toString()).concat(
                "" + proof.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues);

        BigInteger _b = proof.getZ().mod(this.p).multiply(y.modPow(b, this.p)).mod(this.p); // _b = (z (mod p) * (y^b (mod p)) (mod p) = z * y^b (mod p)
        return _a.equals(_b);
    }

    /**
     *
     * @param x secret that current node wants to commit to
     * @return proof of knowledge that participant node knows x in y = g^x
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public ProofOfKnowledge generateProofOfKnowledge(BigInteger y, BigInteger x) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(this.pedersenCommitment.getH(), this.p); // Set generator h of commitments
        // BigInteger y = commitment.calculateCommitment(x); // y = h^x (mod p)
        BigInteger r = this.pedersenCommitment.generateRandom(); // r random
        BigInteger z = commitment.calculateCommitment(r); // z = h^x (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z.toString().concat(
                commitment.getG().toString()).concat(
                y.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues); // b = H( z || h || y || nodeIndex )

        BigInteger a = r.add(b.multiply(x)); // a = r + b*x

        return new ProofOfKnowledge(commitment.getG(), z, a, nodeIndex);

    }

    /**
     *
     * @return proof of knowledge that participant node knows x in c1 = g^x and y in c2 = h^y
     */
    public ProofOfKnowledge[] generateProofOfKnowledgeAND(BigInteger c1, BigInteger c2, BigInteger x, BigInteger y) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return new ProofOfKnowledge[]{generateProofOfKnowledge(c1, x), generateProofOfKnowledge(c2, y)};
    }

    /**
     *
     * @return proof of knowledge that participant node knows either x in h1 = g^x or y in h2 = g^y
     */
    public ProofOfKnowledgeOR generateProofOfKnowledgeOR(BigInteger h1, BigInteger x, BigInteger h2) {
        BigInteger t1 = this.pedersenCommitment.generateRandom();
        BigInteger s2 = this.pedersenCommitment.generateRandom();
        BigInteger c2 = this.pedersenCommitment.generateRandom();

        Commitment commitment = new Commitment(this.pedersenCommitment.getH(), this.p);
        PedersenCommitment pedersenCommitment = new PedersenCommitment(this.pedersenCommitment.getH(), h2, this.q, this.p);

        BigInteger y1 = commitment.calculateCommitment(t1);
        BigInteger y2 = pedersenCommitment.calculateCommitment(s2, c2.negate());

        BigInteger c = this.pedersenCommitment.generateRandom();
        // TODO: Calculate c as a hash of public values

        BigInteger c1 = c.subtract(c2).mod(this.q);

        BigInteger s1 = t1.add(c1.multiply(x));

        return new ProofOfKnowledgeOR(c1, c2, s1, s2, y1, y2);
    }

    public boolean verifyProofOfKnowledgeOR(ProofOfKnowledgeOR proofOfKnowledgeOR, BigInteger h1, BigInteger h2) {
        BigInteger c1 = proofOfKnowledgeOR.getC1();
        BigInteger c2 = proofOfKnowledgeOR.getC2();
        BigInteger s1 = proofOfKnowledgeOR.getS1();
        BigInteger s2 = proofOfKnowledgeOR.getS2();
        BigInteger y1 = proofOfKnowledgeOR.getY1();
        BigInteger y2 = proofOfKnowledgeOR.getY2();

        BigInteger c = this.pedersenCommitment.generateRandom();
        // TODO: Calculate c as a hash of public values
        BigInteger cSum = c1.add(c2);

        Commitment commitment = new Commitment(this.pedersenCommitment.getH(), this.p);
        PedersenCommitment pedersenCommitment1 = new PedersenCommitment(y1, h1, this.q, this.p);
        PedersenCommitment pedersenCommitment2 = new PedersenCommitment(y2, h2, this.q, this.p);

        BigInteger _a = commitment.calculateCommitment(s1);
        BigInteger _b = pedersenCommitment1.calculateCommitment(BigInteger.ONE, c1);

        BigInteger _c = commitment.calculateCommitment(s2);
        BigInteger _d = pedersenCommitment2.calculateCommitment(BigInteger.ONE, c2);

        return c.equals(cSum) && _a.equals(_b) && _c.equals(_d);

    }

}
