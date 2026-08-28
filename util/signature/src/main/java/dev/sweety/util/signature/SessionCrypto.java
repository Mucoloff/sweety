package dev.sweety.util.signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Session crypto shared by client and server.
 *
 * <p>The module-delivery key is <b>derived from the JWT</b> via HMAC-SHA256 and is never
 * transmitted: both sides hold the JWT after auth and derive the same AES-256 key. Module
 * payloads are encrypted with AES-256-GCM (random 12-byte IV prepended, 128-bit tag).
 *
 * <p>Callers must {@link #wipe(byte[]...)} the derived key and any decrypted plaintext as
 * soon as they are consumed (treat like a password buffer).
 */
public final class SessionCrypto {

    private SessionCrypto() {}

    private static final String LABEL_MODULE = "sweety-module-v1";
    private static final String LABEL_HEARTBEAT = "sweety-heartbeat-v1";
    private static final String LABEL_EPOCH = "sweety-epoch-v1";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    /** HMAC-SHA256(key, data) — raw 32-byte tag. */
    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** counter as 8-byte big-endian (deterministic MAC/KDF input on both sides). */
    private static byte[] counterBytes(long counter) {
        return new byte[]{
                (byte) (counter >>> 56), (byte) (counter >>> 48), (byte) (counter >>> 40), (byte) (counter >>> 32),
                (byte) (counter >>> 24), (byte) (counter >>> 16), (byte) (counter >>> 8), (byte) counter
        };
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
        return out;
    }

    /** Derives the 32-byte AES key for module delivery from the session JWT. Never transmitted. */
    public static byte[] deriveModuleKey(String jwt) {
        return hmac(utf8(jwt), utf8(LABEL_MODULE)); // 32 bytes
    }

    /** Key for the heartbeat liveness MAC — proves the client holds the live JWT. */
    public static byte[] deriveHeartbeatKey(String jwt) {
        return hmac(utf8(jwt), utf8(LABEL_HEARTBEAT));
    }

    /**
     * Key for encrypting dynamic bytecode/AI-module chunks for a specific 5-second epoch window.
     *
     * <p>Both sides derive: {@code HMAC(jwt, "sweety-epoch-v1" || counter || nonce)}.
     * The nonce is rolled per-session on the auth server at login and verified on every heartbeat.
     */
    public static byte[] deriveEpochKey(String jwt, long epochCounter, byte[] epochNonce) {
        return hmac(utf8(jwt), concat(utf8(LABEL_EPOCH), counterBytes(epochCounter), epochNonce));
    }

    /**
     * Computes the HMAC-SHA256 tag over (counter || timestamp) using the heartbeat key.
     * Returned to the server on every heartbeat poll to rotate the epoch.
     */
    public static byte[] signHeartbeat(byte[] heartbeatKey, long counter, long timestamp) {
        return hmac(heartbeatKey, concat(counterBytes(counter), counterBytes(timestamp)));
    }

    /** Constant-time verification of a heartbeat tag. */
    public static boolean verifyHeartbeat(byte[] heartbeatKey, long counter, long timestamp, byte[] receivedTag) {
        byte[] expected = signHeartbeat(heartbeatKey, counter, timestamp);
        try {
            return java.security.MessageDigest.isEqual(expected, receivedTag);
        } finally {
            wipe(expected);
        }
    }

    /**
     * Encrypts {@code plaintext} with AES-256-GCM.
     *
     * @param plaintext unencrypted payload (NOT wiped by this method)
     * @param key       32-byte AES key from {@link #deriveModuleKey} or {@link #deriveEpochKey}
     * @return 12-byte IV + ciphertext + 16-byte GCM tag
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        byte[] iv = new byte[GCM_IV_LEN];
        RNG.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return concat(iv, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * Decrypts a payload encrypted by {@link #encrypt(byte[], byte[])}.
     *
     * @param payload 12-byte IV + ciphertext + 16-byte GCM tag
     * @param key     32-byte AES key
     * @return decrypted plaintext
     * @throws GeneralSecurityException if the tag does not verify (tampered / wrong key)
     */
    public static byte[] decrypt(byte[] payload, byte[] key) throws GeneralSecurityException {
        if (payload.length < GCM_IV_LEN + 16) {
            throw new GeneralSecurityException("Payload too short for AES-GCM");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LEN);
        byte[] ciphertext = Arrays.copyOfRange(payload, GCM_IV_LEN, payload.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    /**
     * Overwrites the given byte arrays with zeros in place.
     * Always call in a {@code finally} block for sensitive key buffers and decrypted payloads.
     */
    public static void wipe(byte[]... buffers) {
        for (byte[] b : buffers) {
            if (b != null) Arrays.fill(b, (byte) 0);
        }
    }
}
