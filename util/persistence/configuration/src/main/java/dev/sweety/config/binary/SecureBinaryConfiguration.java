package dev.sweety.config.binary;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A {@link BinaryConfiguration} variant that wraps the serialised payload with:
 * <ol>
 *   <li>Gzip compression (with a magic-signature prefix).</li>
 *   <li>AES-256-GCM authenticated encryption.</li>
 * </ol>
 *
 * <p>On-disk layout: {@code [IV 12 bytes][GCM ciphertext + 16-byte auth tag]}.
 * The plaintext fed into AES is {@code [SIGNATURE 4 bytes][GZIP data]}.</p>
 *
 * <p>The key is derived from a static client-secret string via SHA-256 — it is
 * not a KDF, so security relies on the secret staying private inside the jar.</p>
 */
public class SecureBinaryConfiguration extends BinaryConfiguration {

    private static final int GCM_IV_LEN   = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String CIPHER_ALG = "AES/GCM/NoPadding";

    /** 4-byte magic prepended to the gzip stream before encryption. */
    private final byte[] signature;
    private final SecretKey key;

    /**
     * @param magicChars 4-char magic string used as the signature prefix (e.g. {@code "ALT1"}).
     * @param secretPhrase static secret from which the AES-256 key is derived via SHA-256.
     */
    public SecureBinaryConfiguration(String magicChars, String secretPhrase) {
        super("bin", magicChars, 1);
        if (magicChars == null || magicChars.length() != 4)
            throw new IllegalArgumentException("magicChars must be exactly 4 characters");
        this.signature = magicChars.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        this.key = deriveKey(secretPhrase);
    }

    // ── I/O overrides ────────────────────────────────────────────────────────

    @Override
    protected void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException {
        // 1. Serialise to binary via super
        byte[] raw = toBinary(map);

        // 2. Gzip-compress with signature prefix
        byte[] compressed = compressGzip(raw);

        // 3. AES-GCM encrypt
        byte[] encrypted = aesEncrypt(compressed);

        out.write(encrypted);
        out.flush();
    }

    @Override
    protected Map<String, Object> loadFromStream(InputStream in) throws IOException {
        // 1. Read all bytes
        byte[] encrypted = in.readAllBytes();

        // 2. AES-GCM decrypt
        byte[] compressed = aesDecrypt(encrypted);

        // 3. Verify signature and decompress
        byte[] raw = decompressGzip(compressed);

        // 4. Deserialise via super
        return fromBinary(raw);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Serialise map to raw BinaryConfiguration bytes (using parent implementation). */
    private byte[] toBinary(Map<String, Object> map) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        super.dumpToStream(map, baos);
        return baos.toByteArray();
    }

    /** Deserialise map from raw BinaryConfiguration bytes. */
    private Map<String, Object> fromBinary(byte[] raw) throws IOException {
        return super.loadFromStream(new ByteArrayInputStream(raw));
    }

    /** Write 4-byte signature then GZIP-compress data. */
    private byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length + 32);
        baos.write(signature);
        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(data);
        }
        return baos.toByteArray();
    }

    /** Verify 4-byte signature then GZIP-decompress. */
    private byte[] decompressGzip(byte[] data) throws IOException {
        if (data.length < 4) throw new IOException("Data too short to contain signature");
        for (int i = 0; i < 4; i++) {
            if (data[i] != signature[i])
                throw new IOException("Invalid signature byte at " + i + ": expected 0x"
                        + Integer.toHexString(signature[i] & 0xFF)
                        + " got 0x" + Integer.toHexString(data[i] & 0xFF));
        }
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data, 4, data.length - 4));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gis.transferTo(baos);
            return baos.toByteArray();
        }
    }

    /** AES-256-GCM encrypt; prepends the random 12-byte IV. */
    private byte[] aesEncrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALG);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);

            byte[] out = new byte[GCM_IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, GCM_IV_LEN);
            System.arraycopy(ct, 0, out, GCM_IV_LEN, ct.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encrypt failed", e);
        }
    }

    /** AES-256-GCM decrypt; expects the 12-byte IV prepended. */
    private byte[] aesDecrypt(byte[] data) {
        try {
            if (data.length < GCM_IV_LEN)
                throw new IllegalArgumentException("Ciphertext too short (no IV)");
            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LEN);

            Cipher cipher = Cipher.getInstance(CIPHER_ALG);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(data, GCM_IV_LEN, data.length - GCM_IV_LEN);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM decrypt failed", e);
        }
    }

    /** Derive a 256-bit AES key by SHA-256-hashing the secret phrase. */
    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }
}
