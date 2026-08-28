package dev.sweety.sql4j.api.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing per-user Data Encryption Keys (DEKs) protected by a Master KEK (Key Encryption Key).
 *
 * <p>Supports GDPR <b>Crypto-Shredding</b>: calling {@link #shredUserKey(long)} permanently destroys
 * the in-memory and persisted key, making all encrypted fields completely unrecoverable across cold backups.
 */
public final class CryptoKeyService {

    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final byte[] masterKek;
    private final ConcurrentHashMap<Long, byte[]> dekCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, byte[]> encryptedDekStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private CryptoKeyService(byte[] masterKek) {
        this.masterKek = Objects.requireNonNull(masterKek, "masterKek must not be null");
        if (masterKek.length != 32) {
            throw new IllegalArgumentException("masterKek must be 32 bytes (AES-256)");
        }
    }

    public static CryptoKeyService of(byte[] masterKek) {
        return new CryptoKeyService(masterKek);
    }

    public static CryptoKeyService ofSecret(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, 32));
        return new CryptoKeyService(key);
    }

    /**
     * Retrieves or generates a user's DEK.
     */
    public byte[] getOrCreateUserDek(long userId) {
        return dekCache.computeIfAbsent(userId, id -> {
            byte[] encDek = encryptedDekStore.get(id);
            if (encDek == null) {
                byte[] newDek = new byte[32];
                random.nextBytes(newDek);
                byte[] encrypted = encryptWithKek(newDek);
                encryptedDekStore.put(id, encrypted);
                return newDek;
            }
            return decryptWithKek(encDek);
        });
    }

    /**
     * Encrypts a plaintext string with the user's DEK using AES-GCM.
     */
    public String encrypt(long userId, String plaintext) {
        if (plaintext == null) return null;
        byte[] dek = getOrCreateUserDek(userId);
        if (dek == null) {
            throw new IllegalStateException("DEK shredded or not available for user " + userId);
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), spec);

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt field for user " + userId, e);
        }
    }

    /**
     * Decrypts ciphertext with the user's DEK. If the key was shredded, returns null.
     */
    public String decrypt(long userId, String ciphertextBase64) {
        if (ciphertextBase64 == null) return null;
        byte[] dek = dekCache.get(userId);
        if (dek == null) {
            byte[] encDek = encryptedDekStore.get(userId);
            if (encDek == null) {
                // Key shredded! Data is unreadable.
                return null;
            }
            dek = getOrCreateUserDek(userId);
        }

        try {
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
            if (combined.length < GCM_IV_LENGTH) return null;

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), spec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Key mismatch, bad tag, or shredded key
            return null;
        }
    }

    /**
     * Performs GDPR Crypto-Shredding: permanently deletes the user's DEK from storage and RAM.
     */
    public void shredUserKey(long userId) {
        encryptedDekStore.remove(userId);
        byte[] cached = dekCache.remove(userId);
        if (cached != null) {
            Arrays.fill(cached, (byte) 0); // Zero out key in RAM
        }
    }

    private byte[] encryptWithKek(byte[] plain) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] enc = cipher.doFinal(plain);
            byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return combined;
        } catch (Exception e) {
            throw new RuntimeException("Master KEK encryption failed", e);
        }
    }

    private byte[] decryptWithKek(byte[] combined) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] enc = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, enc, 0, enc.length);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(enc);
        } catch (Exception e) {
            throw new RuntimeException("Master KEK decryption failed", e);
        }
    }
}
