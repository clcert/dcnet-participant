package crypto;

import json.ProofOfKnowledge;
import json.ProofOfKnowledgeOR;
import json.ProofOfKnowledgePedersen;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Camilo J. Gómez (camilo@niclabs.cl)
 */
public class ZeroKnowledgeProof {

    private final int nodeIndex;

    /**
     * Constructor with parameter for crypto.ZeroKnowledgeProof class
     * @param nodeIndex index of current participant node
     */
    public ZeroKnowledgeProof(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    /**
     *
     * @param c commitment s.t. c = g^x h^r (mod p)
     * @param g generator of group G_q
     * @param x value in Z_q
     * @param h generator of group G_q
     * @param r value in Z_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     * @return ProofOfKnowledge s.t. node knows (x,r) in c = g^x h^r (mod p)
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public ProofOfKnowledgePedersen generateProofOfKnowledgePedersen(BigInteger c, BigInteger g, BigInteger x, BigInteger h, BigInteger r, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h, q, p);

        BigInteger y = pedersenCommitment.generateRandom(); // y random value in Z_q
        BigInteger s = pedersenCommitment.generateRandom(); // s random value in Z_q

        BigInteger d = pedersenCommitment.calculateCommitment(y, s); // d = g^y h^s (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = d.toString().concat(
                g.toString()).concat(
                h.toString()).concat(
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
     * @param proof ProofOfKnowledge that node knows (x,r) in c = g^x h^r (mod p)
     * @param c commitment s.t. c = g^x h^r (mod p)
     * @param g generator of group G_q
     * @param h generator of group G_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     * @return true if proof is correct, false otherwise
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public boolean verifyProofOfKnowledgePedersen(ProofOfKnowledgePedersen proof, BigInteger c, BigInteger g, BigInteger h, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h, q, p);

        BigInteger _a = pedersenCommitment.calculateCommitment(proof.getU(), proof.getV()); // _a = g^u h^v (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = proof.getD().toString().concat(
                                   g.toString()).concat(
                                   h.toString()).concat(
                                   c.toString()).concat(
                                   "" + proof.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        BigInteger e = new BigInteger(md.digest());  // e = H( d || g || h || c || nodeIndex )

        BigInteger _b = proof.getD().mod(p).multiply(c.modPow(e , p)).mod(p); // _b = (d (mod p) * (c^e (mod p)) (mod p) = d * c^e (mod p)
        return _a.equals(_b);
    }

    /**
     *
     * @param c commitment s.t. c = g^x (mod p)
     * @param g generator of group G_q
     * @param x value in Z_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     * @return ProofOfKnowledge that node knows x in c = g^x (mod p)
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public ProofOfKnowledge generateProofOfKnowledge(BigInteger c, BigInteger g, BigInteger x, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);

        BigInteger r = commitment.generateRandom(); // r random value in Z_q
        BigInteger z = commitment.calculateCommitment(r); // z = g^r (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z.toString().concat(
                g.toString()).concat(
                c.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues); // b = H( z || g || c || nodeIndex )

        BigInteger a = r.add(b.multiply(x)); // a = r + b*x

        return new ProofOfKnowledge(g, z, a, nodeIndex);

    }

    /**
     *
     * @param proof ProofOfKnowledge that node knows x in c = g^x (mod p)
     * @param c commitment s.t. c = g^x (mod p)
     * @param g generator of group G_q
     * @param q large prime
     * @param p large prime s.t p = kq + 1
     * @return true if proof is correct, false otherwise
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public boolean verifyProofOfKnowledge(ProofOfKnowledge proof, BigInteger c, BigInteger g, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        BigInteger _a = commitment.calculateCommitment(proof.getA()); // _a = g^a (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = proof.getZ().toString().concat(
                g.toString()).concat(
                c.toString()).concat(
                "" + proof.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues); // b = H( z || g || c || nodeIndex )

        BigInteger _b = proof.getZ().mod(p).multiply(c.modPow(b, p)).mod(p); // _b = (z (mod p) * (y^b (mod p)) (mod p) = z * y^b (mod p)
        return _a.equals(_b);
    }

    /**
     *
     * @param c1 commitment s.t. c1 = g^x1 (mod p)
     * @param c2 commitment s.t. c2 = h^x2 (mod p)
     * @param g generator of group G_q
     * @param x1 value in Z_q
     * @param h generator of group G_q
     * @param x2 value in Z_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     * @return ProofOfKnowledge(s) that node knows x1 in c1 = g^x1 (mod p) and x2 in c2 = h^x2 (mod p)
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    public ProofOfKnowledge[] generateProofOfKnowledgeAND(BigInteger c1, BigInteger c2, BigInteger g, BigInteger x1, BigInteger h, BigInteger x2, BigInteger q, BigInteger p) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return new ProofOfKnowledge[]{generateProofOfKnowledge(c1, g, x1, q, p), generateProofOfKnowledge(c2, h, x2, q, p)};
    }

    /**
     *
     * @param h1 commitment s.t. h1 = g^x1 (mod p)
     * @param g generator of group G_q
     * @param x1 value in Z_q
     * @param h2 commitment s.t. h2 = g^x2 (mod p)
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     * @return ProofOfKnowledgeOR that node knows x1 in c1 = g^x1 (mod p) or c2 = g^x2 (mod p)
     */
    public ProofOfKnowledgeOR generateProofOfKnowledgeOR(BigInteger h1, BigInteger g, BigInteger x1, BigInteger h2, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);

        BigInteger t1 = commitment.generateRandom(); // random value in Z_q
        BigInteger s2 = commitment.generateRandom(); // random value in Z_q
        BigInteger c2 = commitment.generateRandom(); // random value in Z_q

        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h2, q, p);

        BigInteger y1 = commitment.calculateCommitment(t1); // y1 = g^t1 (mod p)
        BigInteger y2 = pedersenCommitment.calculateCommitment(s2, c2.negate()); // y2 = g^s2 h2^{-c2}

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = c2.toString().concat(
                g.toString()).concat(
                y1.toString()).concat(
                y2.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger c = new BigInteger(hashOnPublicValues); // c = H( c2 || g || y1 || y2 || nodeIndex )

        BigInteger c1 = c.subtract(c2).mod(q); // c1 = c - c2 (mod q)

        BigInteger s1 = t1.add(c1.multiply(x1)); // s1 = t1 + c1*x1

        return new ProofOfKnowledgeOR(c1, c2, s1, s2, y1, y2, nodeIndex);

    }



    /**
     *
     * @param proofOfKnowledgeOR ProofOfKnowledgeOR that node knows either x1 or x2 in h1 = g^x1 and h2 = g^x2
     * @param h1 commitment s.t. h1 = g^x1 (mod p)
     * @param h2 commitment s.t. h2 = g^x2 (mod p)
     * @param g generator of group G_q
     * @param q large prime
     * @param p large prime s.t. p = kq + 1
     * @return true if proof is correct, false otherwise
     */
    public boolean verifyProofOfKnowledgeOR(ProofOfKnowledgeOR proofOfKnowledgeOR, BigInteger h1, BigInteger h2, BigInteger g, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BigInteger c1 = proofOfKnowledgeOR.getC1();
        BigInteger c2 = proofOfKnowledgeOR.getC2();
        BigInteger s1 = proofOfKnowledgeOR.getS1();
        BigInteger s2 = proofOfKnowledgeOR.getS2();
        BigInteger y1 = proofOfKnowledgeOR.getY1();
        BigInteger y2 = proofOfKnowledgeOR.getY2();

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = c2.toString().concat(
                g.toString()).concat(
                y1.toString()).concat(
                y2.toString()).concat(
                "" + proofOfKnowledgeOR.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger c = new BigInteger(hashOnPublicValues); // c = H( c2 || g || y1 || y2 || nodeIndex )

        BigInteger cSum = c1.add(c2); // cSum = c1 + c2

        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment1 = new PedersenCommitment(y1, h1, q, p);
        PedersenCommitment pedersenCommitment2 = new PedersenCommitment(y2, h2, q, p);

        BigInteger _a = commitment.calculateCommitment(s1); // _a = g^s1 (mod p)
        BigInteger _b = pedersenCommitment1.calculateCommitment(BigInteger.ONE, c1); // _b = y1 h1^c1 (mod p)

        BigInteger _c = commitment.calculateCommitment(s2); // _c = g^s2 (mod p)
        BigInteger _d = pedersenCommitment2.calculateCommitment(BigInteger.ONE, c2); // _d = y2 h2^c2 (mod p)

        return c.equals(cSum) && _a.equals(_b) && _c.equals(_d);

    }

}
