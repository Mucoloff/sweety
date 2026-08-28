package dev.sweety.versioning.server.security;

import dev.sweety.util.logger.SimpleLogger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Signs artifact SHA-256 digests with two independent schemes:
 *
 * <ul>
 *   <li><b>HMAC-SHA256</b> — symmetric, fast; proves the artifact was produced by a holder of the
 *       shared secret. Cheap for server-to-server / clients that share the secret.</li>
 *   <li><b>Ed25519</b> — asymmetric; clients verify with a pinned <em>public</em> key, no shared
 *       secret, and gain non-repudiation. Standard for distributed artifact provenance.</li>
 * </ul>
 *
 * <p>Both sign the 32-byte digest (not the whole file) — constant work regardless of artifact size.
 * Each scheme is independently optional: an unconfigured scheme simply yields {@code null} and its
 * header is omitted. TLS protects transport; these protect the artifact <em>at rest</em> (cache,
 * mirrors) and end-to-end across TLS-terminating proxies.
 */
public final class ArtifactSigner {

    private static final SimpleLogger LOGGER = SimpleLogger.of(ArtifactSigner.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String ED25519 = "Ed25519";

    private final SecretKeySpec hmacKey;  // nullable
    private final PrivateKey ed25519Key;  // nullable

    private ArtifactSigner(SecretKeySpec hmacKey, PrivateKey ed25519Key) {
        this.hmacKey = hmacKey;
        this.ed25519Key = ed25519Key;
    }

    /** Builds a signer from config; missing/invalid material disables that scheme (logged, non-fatal). */
    public static ArtifactSigner of(String hmacSecret, String ed25519KeyPath) {
        SecretKeySpec hmac = (hmacSecret == null || hmacSecret.isBlank())
                ? null
                : new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALG);

        PrivateKey ed = null;
        if (ed25519KeyPath != null && !ed25519KeyPath.isBlank()) {
            try {
                ed = loadEd25519(Path.of(ed25519KeyPath.trim()));
            } catch (Exception e) {
                LOGGER.error("Ed25519 key load failed from " + ed25519KeyPath + "; Ed25519 header disabled", e);
            }
        }
        if (hmac == null) LOGGER.warn("ArtifactSigner: HMAC secret not set, X-Content-HMAC disabled");
        if (ed == null) LOGGER.warn("ArtifactSigner: Ed25519 key not set, X-Content-Ed25519 disabled");
        return new ArtifactSigner(hmac, ed);
    }

    /** @return lowercase hex HMAC-SHA256 of {@code digest}, or {@code null} if HMAC not configured. */
    public String hmacHex(byte[] digest) {
        if (hmacKey == null) return null;
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(hmacKey);
            return toHex(mac.doFinal(digest));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    /** @return base64 Ed25519 signature over {@code digest}, or {@code null} if Ed25519 not configured. */
    public String ed25519Base64(byte[] digest) {
        if (ed25519Key == null) return null;
        try {
            Signature sig = Signature.getInstance(ED25519);
            sig.initSign(ed25519Key);
            sig.update(digest);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 sign failed", e);
        }
    }

    private static PrivateKey loadEd25519(Path pem) throws IOException, GeneralSecurityException {
        String text;
        try (InputStream in = Files.newInputStream(pem)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base64 = text
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance(ED25519).generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String toHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
