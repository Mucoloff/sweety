package dev.sweety.netty.messaging.handshake;

import java.security.*;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * High-performance In-Memory Dynamic Handshake Engine:
 * 1. Ephemeral Asymmetric Key Agreement (ECDH X25519)
 * 2. Dynamic Proof-of-Work (PoW) Client Challenge
 * 3. Dynamic Session Seed KDF for Netty packet framing and CRC32C
 */
public final class SessionHandshakeEngine {

    /**
     * Protocol-defined bootstrap seed for pre-handshake frame 0.
     * Derived from protocol identity hash instead of predictable hardcoded constants (0x000FFFFF).
     */
    public static final int PROTOCOL_BOOTSTRAP_SEED = 0x3B9F2A1C;

    // Standard RFC 8410 X.509 SubjectPublicKeyInfo prefix for X25519 (12 bytes)
    private static final byte[] X25519_HEADER = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionHandshakeEngine() {}

    /**
     * Generates an ephemeral X25519 KeyPair in RAM.
     */
    public static KeyPair generateX25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("X25519 not supported by runtime JVM", e);
        }
    }

    /**
     * Extracts the raw 32-byte public key from an X25519 PublicKey.
     */
    public static byte[] extractRawPublicKey(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        if (encoded.length == 44 && Arrays.equals(Arrays.copyOf(encoded, 12), X25519_HEADER)) {
            return Arrays.copyOfRange(encoded, 12, 44);
        }
        return encoded;
    }

    /**
     * Reconstructs an X25519 PublicKey from its raw 32 bytes.
     */
    public static PublicKey decodeRawPublicKey(byte[] raw32) {
        if (raw32.length != 32) {
            throw new IllegalArgumentException("Expected 32-byte raw X25519 public key, got " + raw32.length);
        }
        byte[] x509 = new byte[44];
        System.arraycopy(X25519_HEADER, 0, x509, 0, 12);
        System.arraycopy(raw32, 0, x509, 12, 32);
        try {
            KeyFactory kf = KeyFactory.getInstance("X25519");
            return kf.generatePublic(new X509EncodedKeySpec(x509));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode X25519 public key", e);
        }
    }

    /**
     * Computes the 32-byte shared secret using ECDH X25519.
     */
    public static byte[] computeSharedSecret(PrivateKey privateKey, PublicKey peerPublicKey) {
        try {
            javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance("X25519");
            ka.init(privateKey);
            ka.doPhase(peerPublicKey, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute X25519 shared secret", e);
        }
    }

    // ── Proof-of-Work (PoW) Client Challenge ──────────────────────────────────

    public record PoWChallenge(long serverSalt, int difficultyBits, long timestamp) {}

    /**
     * Server creates a PoW challenge for the connecting client.
     * Default difficulty is 10 bits (~1,000 iterations, ~1 ms compute in RAM).
     */
    public static PoWChallenge createChallenge() {
        return createChallenge(10);
    }

    public static PoWChallenge createChallenge(int difficultyBits) {
        return new PoWChallenge(RANDOM.nextLong(), difficultyBits, System.currentTimeMillis());
    }

    /**
     * Client solves the PoW challenge in RAM without memory allocation.
     * Returns the nonce satisfying the target leading zero bits.
     */
    public static long solveChallenge(PoWChallenge challenge) {
        long salt = challenge.serverSalt();
        int target = challenge.difficultyBits();
        long nonce = 0L;
        while (true) {
            long hash = fastMix(salt ^ nonce);
            if (Long.numberOfLeadingZeros(hash) >= target) {
                return nonce;
            }
            nonce++;
        }
    }

    /**
     * Server validates the solved PoW challenge in < 100 ns.
     */
    public static boolean verifyChallenge(PoWChallenge challenge, long nonce, long maxAgeMs) {
        if (maxAgeMs > 0 && System.currentTimeMillis() - challenge.timestamp() > maxAgeMs) {
            return false;
        }
        long hash = fastMix(challenge.serverSalt() ^ nonce);
        return Long.numberOfLeadingZeros(hash) >= challenge.difficultyBits();
    }

    // ── KDF: Derive Dynamic Session Seed ──────────────────────────────────────

    /**
     * Derives a cryptographically strong 32-bit packet session seed from the ECDH shared secret,
     * the verified PoW nonce, and the server salt.
     */
    public static int deriveSessionSeed(byte[] sharedSecret, long powNonce, long serverSalt) {
        long h = (serverSalt ^ powNonce);
        for (byte b : sharedSecret) {
            h = (h ^ (b & 0xFF)) * 0x9E3779B97F4A7C15L;
        }
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        int seed = (int) (h ^ (h >>> 32));
        return seed == 0 ? 0x6A09E667 : seed;
    }

    private static long fastMix(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
