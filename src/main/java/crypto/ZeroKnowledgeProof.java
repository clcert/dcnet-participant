package crypto;

import json.ProofOfKnowledge;
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
    private BigInteger p;

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
        this.nodeIndex = nodeIndex;
    }

    /**
     *
     * @param x secret that current node wants to commit to
     * @param r random value needed for commitment
     * @return proof of knowledge that participant node knows x in c = g^x h^r
     */
    public ProofOfKnowledgePedersen generateProofOfKnowledgePedersen(BigInteger x, BigInteger r) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BigInteger c = this.pedersenCommitment.calculateCommitment(x, r); // c = g^x h^r (mod p)

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

}
