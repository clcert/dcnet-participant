package crypto;

import com.google.gson.Gson;
import json.ProofOfKnowledge;

import java.math.BigInteger;

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
    public String generateProofOfKnowledge(BigInteger x, BigInteger r) {
        BigInteger c = this.pedersenCommitment.calculateCommitment(x, r); // c = g^x h^r (mod p)

        BigInteger y = this.pedersenCommitment.generateRandom(); // y random
        BigInteger s = this.pedersenCommitment.generateRandom(); // s random

        BigInteger d = this.pedersenCommitment.calculateCommitment(y, s); // d = g^y h^s (mod p)
        BigInteger e = this.pedersenCommitment.generateRandom(); // TODO: e random must be calculated as H(...)

        BigInteger u = e.multiply(x).add(y);
        BigInteger v = e.multiply(r).add(s);

        ProofOfKnowledge proof = new ProofOfKnowledge(c, d, e, u, v, nodeIndex);

        return new Gson().toJson(proof, ProofOfKnowledge.class);
    }

    /**
     *
     * @param proof proof of knowledge that participant node knows x in commitment inside
     * @return true if the verifying process is succeed, false otherwise
     */
    public boolean verifyProofOfKnowledge(ProofOfKnowledge proof) {
        BigInteger _a = this.pedersenCommitment.calculateCommitment(proof.getU(), proof.getV()); // _a = g^u h^v (mod p)
        BigInteger _b = proof.getD().mod(this.p).multiply(proof.getC().modPow(proof.getE(), this.p)).mod(this.p); // _b = (d (mod p) * (c^e (mod p)) (mod p) = d * c^e (mod p)
        return _a.equals(_b);
    }

}
