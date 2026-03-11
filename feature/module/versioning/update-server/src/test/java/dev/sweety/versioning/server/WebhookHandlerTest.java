package dev.sweety.versioning.server;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookHandlerTest {

    @Test
    void validatesHmacSha256Signature() throws Exception {
        String secret = "super-secret";
        byte[] body = "{\"app\":\"1.2.0\"}".getBytes(StandardCharsets.UTF_8);

        String signature = "sha256=" + hmacHex(secret, body);

        assertTrue(WebhookHandler.isValidSignature(signature, body, secret));
        assertFalse(WebhookHandler.isValidSignature("sha256=deadbeef", body, secret));
    }

    private static String hmacHex(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

