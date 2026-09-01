package dev.sweety.transform.security;

import dev.sweety.transform.transformers.security.PayloadProtectionTransformer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class PayloadProtectionTransformerTest {

    @Test
    public void testPayloadEncryptionDecryptionRoundTrip() {
        byte[] secretPayload = "CLIENT_AES_PAYLOAD_KEY_2026_SWEETY".getBytes(StandardCharsets.UTF_8);
        byte key = 0x5A;

        byte[] encrypted = PayloadProtectionTransformer.encryptPayload(secretPayload, key);
        assertNotNull(encrypted);
        assertFalse(Arrays.equals(secretPayload, encrypted), "Encrypted payload must differ from plaintext");

        byte[] decrypted = PayloadProtectionTransformer.decryptPayload(encrypted, key);
        assertArrayEquals(secretPayload, decrypted, "Decrypted payload must match original plaintext");
    }
}
