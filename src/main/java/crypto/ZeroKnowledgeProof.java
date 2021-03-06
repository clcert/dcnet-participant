package crypto;

import json.*;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Class that manages Zero Knowledge Proofs operations, using regular and Pedersen commitments
 *
 * @author Camilo J. Gomez (camilo@niclabs.cl)
 */
public class ZeroKnowledgeProof {

    /**
     * Index of current node
     */
    private final int nodeIndex;

    /**
     * Constructor with parameter for crypto.ZeroKnowledgeProof class
     *
     * @param nodeIndex index of current participant node
     */
    public ZeroKnowledgeProof(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    /**
     * Generate Proof of Knowledge that participant knows \((x, r)\) in \(c = g^x h^r \pmod{p}\)
     *
     * @param c commitment s.t. \(c = g^x h^r \pmod{p}\)
     * @param g generator of group \(G_q\)
     * @param x value in \(\mathbb{Z}_q\)
     * @param h generator of group \(G_q\)
     * @param r value in \(\mathbb{Z}_q\)
     * @param q large prime
     * @param p large prime s.t. \(p = kq + 1\)
     * @return ProofOfKnowledge s.t. node knows \((x,r)\) in \(c = g^x h^r \pmod{p}\)
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
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
        BigInteger e = new BigInteger(hashOnPublicValues).mod(q); // e = H( d || g || h || c || nodeIndex ) (mod q)

        BigInteger u = e.multiply(x).add(y); // u = e*x + y
        BigInteger v = e.multiply(r).add(s); // v = e*r + s

        return new ProofOfKnowledgePedersen(d, u, v, nodeIndex);

    }

    /**
     * Verifies if the Proof of Knowledge provide is valid or not for knowing \((x, r)\) in \(c = g^x h^r \pmod{p}\)
     *
     * @param proof ProofOfKnowledge that node knows \((x,r)\) in \(c = g^x h^r \pmod{p}\)
     * @param c     commitment s.t. \(c = g^x h^r \pmod{p}\)
     * @param g     generator of group \(G_q\)
     * @param h     generator of group \(G_q\)
     * @param q     large prime
     * @param p     large prime s.t. \(p = kq + 1\)
     * @return true if proof is correct, false otherwise
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
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
        BigInteger e = new BigInteger(md.digest()).mod(q);  // e = H( d || g || h || c || nodeIndex ) (mod q)

        BigInteger _b = proof.getD().mod(p).multiply(c.modPow(e, p)).mod(p); // _b = (d (mod p) * (c^e (mod p)) (mod p) = d * c^e (mod p)
        return _a.equals(_b);
    }

    /**
     * Generate Proof of Knowledge that participant knows \(x\) in \(c = g^x \pmod{p}\)
     *
     * @param c commitment s.t. \(c = g^x \pmod{p}\)
     * @param g generator of group \(G_q\)
     * @param x value in \(\mathbb{Z}_q\)
     * @param q large prime
     * @param p large prime s.t. \(p = kq + 1\)
     * @return ProofOfKnowledge that node knows \(x\) in \(c = g^x \pmod{p}\)
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
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
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z || g || c || nodeIndex ) (mod q)

        BigInteger a = r.add(b.multiply(x)); // a = r + b*x

        return new ProofOfKnowledge(g, z, a, nodeIndex);

    }

    /**
     * Verifies if the Proof of Knowledge provide is correct or not for knowing \(x\) in \(c = g^x \pmod{p}\)
     *
     * @param proof ProofOfKnowledge that node knows \(x\) in \(c = g^x \pmod{p}\)
     * @param c     commitment s.t. \(c = g^x \pmod{p}\)
     * @param g     generator of group \(G_q\)
     * @param q     large prime
     * @param p     large prime s.t \(p = kq + 1\)
     * @return true if proof is correct, false otherwise
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
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
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z || g || c || nodeIndex ) (mod q)

        BigInteger _b = proof.getZ().mod(p).multiply(c.modPow(b, p)).mod(p); // _b = (z (mod p) * (c^b (mod p)) (mod p) = z * c^b (mod p)
        return _a.equals(_b);
    }

    /**
     * Generates Proof of Knowledge that participants knows \(x_1\) in \(c_1 = g^{x_1} \pmod{p} \lor (c_2 = g^{x_2} \pmod{p} \land c_3 = g^{x_3} \pmod{p})\)
     *
     * @param h1 commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param g  generator of group \(G_q\)
     * @param x1 value in \(\mathbb{Z}_q\)
     * @param h2 commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param h3 commitment s.t. \(h_3 = g^{x_3} \pmod{p}\)
     * @param q  large prime
     * @param p  large prime s.t. \(p = kq + 1\)
     * @return ProofOfKnowledgeMessageFormat that node knows \(x_1\) in \(c_1 = g^{x_1} \pmod{p} \lor (c_2 = g^{x_2} \pmod{p} \land c_3 = g^{x_3} \pmod{p})\)
     * @throws UnsupportedEncodingException test
     * @throws NoSuchAlgorithmException     test
     */
    public ProofOfKnowledgeMessageFormat generateProofOfKnowledgeMessageFormatX1(BigInteger h1, BigInteger g, BigInteger x1, BigInteger h2, BigInteger h3, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment2 = new PedersenCommitment(g, h2, q, p);
        PedersenCommitment pedersenCommitment3 = new PedersenCommitment(g, h3, q, p);

        BigInteger c2 = commitment.generateRandom(); // c2 random value in Z_q
        BigInteger r1 = commitment.generateRandom(); // r1 random value in Z_q
        BigInteger r2 = commitment.generateRandom(); // r2 random value in Z_q
        BigInteger r3 = commitment.generateRandom(); // r3 random value in Z_q

        BigInteger z1 = commitment.calculateCommitment(r1); // z1 = g^r1 (mod p)
        BigInteger z2 = pedersenCommitment2.calculateCommitment(r2, c2.negate()); // z2 = g^r2 h2^{-c2}
        BigInteger z3 = pedersenCommitment3.calculateCommitment(r3, c2.negate()); // z3 = g^r3 h3^{-c2}

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                z3.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                h3.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || z3 || g || h1 || h2 || h3 || nodeIndex )

        BigInteger c1 = b.subtract(c2).mod(q); // c1 = b - c2 (mod q)

        BigInteger a1 = r1.add(c1.multiply(x1)); // a1 = r1 + c1*x1

        return new ProofOfKnowledgeMessageFormat(c1, c2, z1, z2, z3, a1, r2, r3, nodeIndex);

    }

    /**
     * Generates Proof of Knowledge that participants knows \((x_2, x_3)\) in \(h_1 = g^{x_1} \pmod{p} \lor (h_2 = g^{x_2} \pmod{p} \land h_3 = g^{x_3} \pmod{p})\)
     *
     * @param h1 commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param g  generator of group \(G_q\)
     * @param h2 commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param x2 value in \(\mathbb{Z}_q\)
     * @param h3 commitment s.t. \(h_3 = g^{x_3} \pmod{p}\)
     * @param x3 value in \(\mathbb{Z}_q\)
     * @param q  large prime
     * @param p  large prime s.t. \(p = kq + 1\)
     * @return ProofOfKnowledgeMessageFormat that node knows \((x_2, x_3)\) in \(h_1 = g^{x_1} \pmod{p} \lor (h_2 = g^{x_2} \pmod{p} \land h_3 = g^{x_3} \pmod{p})\)
     * @throws UnsupportedEncodingException test
     * @throws NoSuchAlgorithmException     test
     */
    public ProofOfKnowledgeMessageFormat generateProofOfKnowledgeMessageFormatX2X3(BigInteger h1, BigInteger g, BigInteger h2, BigInteger x2, BigInteger h3, BigInteger x3, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment1 = new PedersenCommitment(g, h1, q, p);

        BigInteger c1 = commitment.generateRandom(); // c1 random value in Z_q
        BigInteger r1 = commitment.generateRandom(); // r1 random value in Z_q
        BigInteger r2 = commitment.generateRandom(); // r2 random value in Z_q
        BigInteger r3 = commitment.generateRandom(); // r3 random value in Z_q

        BigInteger z1 = pedersenCommitment1.calculateCommitment(r1, c1.negate()); // z1 = g^r1 h1^{-c1} (mod p)
        BigInteger z2 = commitment.calculateCommitment(r2); // z2 = g^r2 (mod p)
        BigInteger z3 = commitment.calculateCommitment(r3); // z3 = g^r3 (mod p)

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                z3.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                h3.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || z3 || g || h1 || h2 || h3 || nodeIndex )

        BigInteger c2 = b.subtract(c1).mod(q); // c2 = b - c1 (mod q)

        BigInteger a2 = r2.add(c2.multiply(x2)); // a2 = r2 + c2*x2
        BigInteger a3 = r3.add(c2.multiply(x3)); // a3 = r3 + c2*x3

        return new ProofOfKnowledgeMessageFormat(c1, c2, z1, z2, z3, r1, a2, a3, nodeIndex);

    }


    /**
     * Verifies if the Proof of Knowledge provide is correct or not for knowing either \(x_1\) or \(x_2\) in \(h_1 = g^{x_1} \pmod{p} \lor (h_2 = g^{x_2} \pmod{p} \land h_3 = g^{x_3} \pmod{p})\)
     *
     * @param proofOfKnowledgeMessageFormat ProofOfKnowledgeMessageFormat that node knows either \(x_1\) or \(x_2\) in \(h_1 = g^{x_1} \pmod{p} \lor (h_2 = g^{x_2} \pmod{p} \land h_3 = g^{x_3} \pmod{p})\)
     * @param h1                            commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param h2                            commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param h3                            commitment s.t. \(h_3 = g^{x_3} \pmod{p}\)
     * @param g                             generator of group \(G_q\)
     * @param q                             large prime
     * @param p                             large prime s.t. \(p = kq + 1\)
     * @return true if proof is correct, false otherwise
     * @throws UnsupportedEncodingException test
     * @throws NoSuchAlgorithmException     test
     */
    public boolean verifyProofOfKnowledgeMessageFormat(ProofOfKnowledgeMessageFormat proofOfKnowledgeMessageFormat, BigInteger h1, BigInteger h2, BigInteger h3, BigInteger g, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BigInteger c1 = proofOfKnowledgeMessageFormat.getC1();
        BigInteger c2 = proofOfKnowledgeMessageFormat.getC2();
        BigInteger z1 = proofOfKnowledgeMessageFormat.getZ1();
        BigInteger z2 = proofOfKnowledgeMessageFormat.getZ2();
        BigInteger z3 = proofOfKnowledgeMessageFormat.getZ3();
        BigInteger a1 = proofOfKnowledgeMessageFormat.getA1();
        BigInteger a2 = proofOfKnowledgeMessageFormat.getA2();
        BigInteger a3 = proofOfKnowledgeMessageFormat.getA3();

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                z3.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                h3.toString()).concat(
                "" + proofOfKnowledgeMessageFormat.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || z3 || g || h1 || h2 || h3 || nodeIndex ) (mod q)

        BigInteger cSum = c1.add(c2).mod(q); // cSum = c1 + c2

        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment1 = new PedersenCommitment(z1, h1, q, p);
        PedersenCommitment pedersenCommitment2 = new PedersenCommitment(z2, h2, q, p);
        PedersenCommitment pedersenCommitment3 = new PedersenCommitment(z3, h3, q, p);

        BigInteger _a = commitment.calculateCommitment(a1); // _a = g^r1 (mod p)
        BigInteger _b = pedersenCommitment1.calculateCommitment(BigInteger.ONE, c1); // _b = z1 h1^c1 (mod p)

        BigInteger _c = commitment.calculateCommitment(a2); // _c = g^r2 (mod p)
        BigInteger _d = pedersenCommitment2.calculateCommitment(BigInteger.ONE, c2); // _d = z2 h2^c2 (mod p)

        BigInteger _e = commitment.calculateCommitment(a3); // _e = g^r3 (mod p)
        BigInteger _f = pedersenCommitment3.calculateCommitment(BigInteger.ONE, c2); // _f = z3 h3^c2 (mod p)

        return b.equals(cSum) && _a.equals(_b) && _c.equals(_d) && _e.equals(_f);

    }

    /**
     * Generates Proof of Knowledge that participant knows \(x_1\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     *
     * @param h1 commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param g  generator of group \(G_q\)
     * @param x1 value in \(\mathbb{Z}_q\)
     * @param h2 commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param q  large prime
     * @param p  large prime s.t. \(p = kq + 1\)
     * @return Proof of Knowledge that participant knows \(x_1\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
     */
    public ProofOfKnowledgeResendingFatherRoundReal generateProofOfKnowledgeResendingFatherRoundRealX1(BigInteger h1, BigInteger g, BigInteger x1, BigInteger h2, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h2, q, p);

        BigInteger c2 = commitment.generateRandom();
        BigInteger r1 = commitment.generateRandom();
        BigInteger r2 = commitment.generateRandom();

        BigInteger z1 = commitment.calculateCommitment(r1);
        BigInteger z2 = pedersenCommitment.calculateCommitment(r2, c2.negate());

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || g || h1 || h2 || nodeIndex )

        BigInteger c1 = b.subtract(c2).mod(q);

        BigInteger a1 = r1.add(c1.multiply(x1));

        return new ProofOfKnowledgeResendingFatherRoundReal(c1, c2, z1, z2, a1, r2, nodeIndex);

    }

    /**
     * Generates Proof of Knowledge that participant knows \(x_2\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     *
     * @param h1 commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param h2 commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param g  generator of group \(G_q\)
     * @param x2 value in \(\mathbb{Z}_q\)
     * @param q  large prime
     * @param p  large prime s.t. \(p = kq + 1\)
     * @return Proof of Knowledge that participant knows \(x_1\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
     */
    public ProofOfKnowledgeResendingFatherRoundReal generateProofOfKnowledgeResendingFatherRoundRealX2(BigInteger h1, BigInteger h2, BigInteger g, BigInteger x2, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h1, q, p);

        BigInteger c1 = commitment.generateRandom();
        BigInteger r1 = commitment.generateRandom();
        BigInteger r2 = commitment.generateRandom();

        BigInteger z1 = pedersenCommitment.calculateCommitment(r1, c1.negate());
        BigInteger z2 = commitment.calculateCommitment(r2);

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || g || h1 || h2 || nodeIndex )

        BigInteger c2 = b.subtract(c1).mod(q);

        BigInteger a2 = r2.add(c2.multiply(x2));

        return new ProofOfKnowledgeResendingFatherRoundReal(c1, c2, z1, z2, r1, a2, nodeIndex);

    }

    /**
     * Verifies if the Proof of Knowledge provide is correct or not for knowing either \(x_1\) or \(x_2\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     *
     * @param proofOfKnowledgeResendingFatherRoundReal Proof of Knowledge that participants knows either \(x_1\) or \(x_2\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     * @param h1                                       commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param h2                                       commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param g                                        generator of group \(G_q\)
     * @param q                                        large prime
     * @param p                                        large prime s.t. \(p = kq + 1\)
     * @return true if proof is correct, false otherwise
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
     */
    public boolean verifyProofOfKnowledgeResendingFatherRoundReal(ProofOfKnowledgeResendingFatherRoundReal proofOfKnowledgeResendingFatherRoundReal, BigInteger h1, BigInteger h2, BigInteger g, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BigInteger c1 = proofOfKnowledgeResendingFatherRoundReal.getC1();
        BigInteger c2 = proofOfKnowledgeResendingFatherRoundReal.getC2();
        BigInteger z1 = proofOfKnowledgeResendingFatherRoundReal.getZ1();
        BigInteger z2 = proofOfKnowledgeResendingFatherRoundReal.getZ2();
        BigInteger a1 = proofOfKnowledgeResendingFatherRoundReal.getA1();
        BigInteger a2 = proofOfKnowledgeResendingFatherRoundReal.getA2();

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                "" + proofOfKnowledgeResendingFatherRoundReal.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || g || h1 || h2 || nodeIndex ) (mod q)

        BigInteger cSum = c1.add(c2).mod(q); // cSum = c1 + c2

        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment1 = new PedersenCommitment(z1, h1, q, p);
        PedersenCommitment pedersenCommitment2 = new PedersenCommitment(z2, h2, q, p);

        BigInteger _a = commitment.calculateCommitment(a1);
        BigInteger _b = pedersenCommitment1.calculateCommitment(BigInteger.ONE, c1);

        BigInteger _c = commitment.calculateCommitment(a2);
        BigInteger _d = pedersenCommitment2.calculateCommitment(BigInteger.ONE, c2);

        return b.equals(cSum) && _a.equals(_b) && _c.equals(_d);

    }

    /**
     * Generates Proof of Knowledge that participant knows \(x_1\) in \(h_1 = g^{x_1} \lor (h_2 = g^{x_2} \land (\bigwedge h_j = g^{x_j})\)
     *
     * @param h1 commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param g  generator of group \(G_q\)
     * @param x1 value in \(\mathbb{Z}_q\)
     * @param h2 commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param hj array of commitments s.t. \(h_j = g^{x_j} \pmod{p}\)
     * @param q  large prime
     * @param p  large prime s.t. \(p = kq + 1\)
     * @return Proof of Knowledge that participant knows \(x_1\) in \(h_1 = g^{x_1} \lor (h_2 = g^{x_2} \land (\bigwedge h_j = g^{x_j})\)
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
     */
    public ProofOfKnowledgeResendingFatherRoundVirtual generateProofOfKnowledgeResendingFatherRoundVirtualX1(BigInteger h1, BigInteger g, BigInteger x1, BigInteger h2, BigInteger[] hj, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h2, q, p);
        PedersenCommitment[] pedersenCommitmentJ = new PedersenCommitment[hj.length];
        for (int i = 0; i < hj.length; i++) {
            pedersenCommitmentJ[i] = new PedersenCommitment(g, hj[i], q, p);
        }

        BigInteger c2 = commitment.generateRandom();
        /*BigInteger[] cj = new BigInteger[hj.length];
        for (int i = 0; i < cj.length; i++) {
            cj[i] = commitment.generateRandom();
        }*/
        BigInteger r1 = commitment.generateRandom();
        BigInteger r2 = commitment.generateRandom();
        BigInteger[] rj = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            rj[i] = commitment.generateRandom();
        }

        BigInteger z1 = commitment.calculateCommitment(r1);
        BigInteger z2 = pedersenCommitment.calculateCommitment(r2, c2.negate());
        BigInteger[] zj = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            zj[i] = pedersenCommitmentJ[i].calculateCommitment(rj[i], c2.negate());
        }

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || g || h1 || h2 || nodeIndex )

        BigInteger c1 = b.subtract(c2).mod(q);

        BigInteger a1 = r1.add(c1.multiply(x1));

        return new ProofOfKnowledgeResendingFatherRoundVirtual(c1, c2, z1, z2, zj, a1, r2, rj, nodeIndex);

    }

    /**
     * Generates Proof of Knowledge that participant knows \(x_2 \land x_j\) in \(h_1 = g^{x_1} \lor (h_2 = g^{x_2} \land (\bigwedge h_j = g^{x_j})\)
     *
     * @param h1 commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param h2 commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param hj array of commitments s.t. \(h_j = g^{x_j} \pmod{p}\)
     * @param g  generator of group \(G_q\)
     * @param x2 value in \(\mathbb{Z}_q\)
     * @param xj values in \(\mathbb{Z}_q\)
     * @param q  large prime
     * @param p  large prime s.t. \(p = kq + 1\)
     * @return Proof of Knowledge that participant knows \(x_2 \land x_j\) in \(h_1 = g^{x_1} \lor (h_2 = g^{x_2} \land (\bigwedge h_j = g^{x_j})\)
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
     */
    public ProofOfKnowledgeResendingFatherRoundVirtual generateProofOfKnowledgeResendingFatherRoundVirtualX2Xs(BigInteger h1, BigInteger h2, BigInteger[] hj, BigInteger g, BigInteger x2, BigInteger[] xj, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment = new PedersenCommitment(g, h1, q, p);

        BigInteger c1 = commitment.generateRandom();
        BigInteger r1 = commitment.generateRandom();
        BigInteger r2 = commitment.generateRandom();
        BigInteger[] rj = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            rj[i] = commitment.generateRandom();
        }

        BigInteger z1 = pedersenCommitment.calculateCommitment(r1, c1.negate());
        BigInteger z2 = commitment.calculateCommitment(r2);
        BigInteger[] zj = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            zj[i] = commitment.calculateCommitment(rj[i]);
        }

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                "" + this.nodeIndex);
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || g || h1 || h2 || nodeIndex )

        BigInteger c2 = b.subtract(c1).mod(q);
        /*BigInteger[] cj = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            cj[i] = b.subtract(c1).mod(q); // Check this
        }*/

        BigInteger a2 = r2.add(c2.multiply(x2));
        BigInteger[] aj = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            aj[i] = rj[i].add(c2.multiply(xj[i]));
        }

        return new ProofOfKnowledgeResendingFatherRoundVirtual(c1, c2, z1, z2, zj, r1, a2, aj, nodeIndex);

    }

    /**
     * Verifies if the Proof of Knowledge provide is correct or not for knowing either \(x_1\) or \(x_2\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     *
     * @param proofOfKnowledgeResendingFatherRoundVirtual Proof of Knowledge that participants knows either \(x_1\) or \(x_2\) in \(h_1 = g^{x_1} \lor h_2 = g^{x_2}\)
     * @param h1                                          commitment s.t. \(h_1 = g^{x_1} \pmod{p}\)
     * @param h2                                          commitment s.t. \(h_2 = g^{x_2} \pmod{p}\)
     * @param g                                           generator of group \(G_q\)
     * @param q                                           large prime
     * @param p                                           large prime s.t. \(p = kq + 1\)
     * @return true if proof is correct, false otherwise
     * @throws NoSuchAlgorithmException     test
     * @throws UnsupportedEncodingException test
     */
    public boolean verifyProofOfKnowledgeResendingFatherRoundVirtual(ProofOfKnowledgeResendingFatherRoundVirtual proofOfKnowledgeResendingFatherRoundVirtual, BigInteger h1, BigInteger h2, BigInteger[] hj, BigInteger g, BigInteger q, BigInteger p) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BigInteger c1 = proofOfKnowledgeResendingFatherRoundVirtual.getC1();
        BigInteger c2 = proofOfKnowledgeResendingFatherRoundVirtual.getC2();
        // BigInteger[] cj = proofOfKnowledgeResendingFatherRoundVirtual.getCj();
        BigInteger z1 = proofOfKnowledgeResendingFatherRoundVirtual.getZ1();
        BigInteger z2 = proofOfKnowledgeResendingFatherRoundVirtual.getZ2();
        BigInteger[] zj = proofOfKnowledgeResendingFatherRoundVirtual.getZj();
        BigInteger a1 = proofOfKnowledgeResendingFatherRoundVirtual.getA1();
        BigInteger a2 = proofOfKnowledgeResendingFatherRoundVirtual.getA2();
        BigInteger[] aj = proofOfKnowledgeResendingFatherRoundVirtual.getAj();

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String publicValueOnHash = z1.toString().concat(
                z2.toString()).concat(
                g.toString()).concat(
                h1.toString()).concat(
                h2.toString()).concat(
                "" + proofOfKnowledgeResendingFatherRoundVirtual.getNodeIndex());
        md.update(publicValueOnHash.getBytes("UTF-8"));
        byte[] hashOnPublicValues = md.digest();
        BigInteger b = new BigInteger(hashOnPublicValues).mod(q); // b = H( z1 || z2 || g || h1 || h2 || nodeIndex ) (mod q)

        BigInteger cSum = c1.add(c2).mod(q); // cSum = c1 + c2

        Commitment commitment = new Commitment(g, q, p);
        PedersenCommitment pedersenCommitment1 = new PedersenCommitment(z1, h1, q, p);
        PedersenCommitment pedersenCommitment2 = new PedersenCommitment(z2, h2, q, p);
        PedersenCommitment[] pedersenCommitmentJ = new PedersenCommitment[hj.length];
        for (int i = 0; i < hj.length; i++) {
            pedersenCommitmentJ[i] = new PedersenCommitment(zj[i], hj[i], q, p);
        }

        BigInteger _a = commitment.calculateCommitment(a1);
        BigInteger _b = pedersenCommitment1.calculateCommitment(BigInteger.ONE, c1);

        BigInteger _c = commitment.calculateCommitment(a2);
        BigInteger _d = pedersenCommitment2.calculateCommitment(BigInteger.ONE, c2);

        BigInteger[] _e = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            _e[i] = commitment.calculateCommitment(aj[i]);
        }
        BigInteger[] _f = new BigInteger[hj.length];
        for (int i = 0; i < hj.length; i++) {
            _f[i] = pedersenCommitmentJ[i].calculateCommitment(BigInteger.ONE, c2);
        }

        boolean condition = true;
        for (int i = 0; i < hj.length; i++) {
            if (!(_e[i].equals(_f[i]))) {
                condition = false;
            }
        }

        return b.equals(cSum) && _a.equals(_b) && _c.equals(_d) && condition;

    }

}
