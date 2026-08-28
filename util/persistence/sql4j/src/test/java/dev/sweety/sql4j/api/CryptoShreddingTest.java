package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.crypto.CryptoKeyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CryptoShreddingTest {

    @Test
    void testEncryptionAndCryptoShredding() {
        CryptoKeyService crypto = CryptoKeyService.ofSecret("super_secure_master_kek_secret_key_32b");
        long userId = 424242L;
        String piiEmail = "user.secret@protonmail.com";

        // Encrypt with user's DEK
        String ciphertext = crypto.encrypt(userId, piiEmail);
        assertNotNull(ciphertext);
        assertNotEquals(piiEmail, ciphertext);

        // Decrypt successfully before shredding
        String decrypted = crypto.decrypt(userId, ciphertext);
        assertEquals(piiEmail, decrypted);

        // Perform GDPR Crypto-Shredding (deletes DEK)
        crypto.shredUserKey(userId);

        // Decryption must now fail and return null (data is unrecoverable noise)
        String shreddedAttempt = crypto.decrypt(userId, ciphertext);
        assertNull(shreddedAttempt, "Decryption must return null after DEK is shredded");
    }
}
