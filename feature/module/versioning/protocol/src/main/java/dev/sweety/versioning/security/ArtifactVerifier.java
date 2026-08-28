package dev.sweety.versioning.security;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Client-side verification of a downloaded artifact against the update-server integrity headers
 * ({@code X-Content-SHA256} / {@code X-Content-HMAC} / {@code X-Content-Ed25519}). Shared by the
 * launcher and the core-mod bootstrap download paths.
 *
 * <p>Layered:
 * <ol>
 *   <li><b>SHA-256</b> — locally computed digest must equal the header. Catches corruption / naive
 *       tampering, but a forged header would also forge this.</li>
 *   <li><b>Ed25519</b> — signature over the digest, verified with a <em>pinned public key</em>. The
 *       real tamper protection: unforgeable without the server private key, independent of TLS.</li>
 *   <li><b>HMAC-SHA256</b> — over the digest with a shared secret; symmetric alternative.</li>
 * </ol>
 *
 * <p>When {@code required} and no authenticated scheme (Ed25519/HMAC) can be checked, verification
 * fails closed — SHA-256 alone is not authentication.
 */
public final class ArtifactVerifier {

    private static final SimpleLogger logger = SimpleLogger.of(ArtifactVerifier.class);

    private static final String HMAC_ALG = "HmacSHA256";
    private static final String ED25519 = "Ed25519";

    // Config sources (system property takes precedence over env).
    private static final String PROP_PUBKEY = "luce.ed25519.pubkey";
    private static final String ENV_PUBKEY = "LUCE_ED25519_PUBKEY";
    private static final String PROP_HMAC = "luce.integrity.hmac";
    private static final String ENV_HMAC = "INTEGRITY_HMAC_SECRET";
    private static final String PROP_REQUIRED = "luce.verify.integrity";
    private static final String ENV_REQUIRED = "LUCE_VERIFY_INTEGRITY";

    public record Result(boolean ok, String reason) {
        static Result pass(String reason) { return new Result(true, reason); }
        static Result fail(String reason) { return new Result(false, reason); }
    }

    private final boolean required;
    private final PublicKey ed25519Pub;   // nullable
    private final SecretKeySpec hmacKey;   // nullable

    private ArtifactVerifier(boolean required, PublicKey ed25519Pub, SecretKeySpec hmacKey) {
        this.required = required;
        this.ed25519Pub = ed25519Pub;
        this.hmacKey = hmacKey;
    }

    /**
     * @param ed25519PublicKeyBase64 X.509/SPKI public key, base64 (PEM headers stripped); blank = none
     * @param hmacSecret             shared HMAC secret; blank = none
     * @param required               fail closed if no authenticated scheme can be checked
     */
    public static ArtifactVerifier of(String ed25519PublicKeyBase64, String hmacSecret, boolean required) {
        PublicKey pub = null;
        if (ed25519PublicKeyBase64 != null && !ed25519PublicKeyBase64.isBlank()) {
            try {
                String b64 = ed25519PublicKeyBase64
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");
                byte[] der = Base64.getDecoder().decode(b64);
                pub = KeyFactory.getInstance(ED25519).generatePublic(new X509EncodedKeySpec(der));
            } catch (Exception e) {
                logger.profile("init").warn("Ed25519 public key parse failed; Ed25519 disabled: " + e.getMessage());
            }
        }
        SecretKeySpec hmac = (hmacSecret == null || hmacSecret.isBlank())
                ? null
                : new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALG);
        return new ArtifactVerifier(required, pub, hmac);
    }

    /**
     * Builds a verifier from system properties / environment. The core-mod bootstrap sets
     * {@code luce.ed25519.pubkey} from its build-pinned key. Default {@code required} is true when
     * a public key is present (a delivered build), false otherwise (dev / unsigned).
     */
    public static ArtifactVerifier fromConfig() {
        String pub = resolve(PROP_PUBKEY, ENV_PUBKEY, "");
        String hmac = resolve(PROP_HMAC, ENV_HMAC, "");
        String requiredRaw = resolve(PROP_REQUIRED, ENV_REQUIRED, "");
        boolean required = requiredRaw.isBlank() ? !pub.isBlank() : Boolean.parseBoolean(requiredRaw);
        return of(pub, hmac, required);
    }

    public boolean isRequired() {
        return required;
    }

    /** Convenience: hashes {@code file} then {@link #verify}. */
    public Result verifyFile(Path file, String sha256Header, String hmacHeader, String ed25519Header) throws IOException {
        return verify(sha256(file), sha256Header, hmacHeader, ed25519Header);
    }

    /**
     * @param digest        locally computed SHA-256 of the received bytes
     * @param sha256Header  {@code X-Content-SHA256} (nullable)
     * @param hmacHeader    {@code X-Content-HMAC} (nullable)
     * @param ed25519Header {@code X-Content-Ed25519} (nullable)
     */
    public Result verify(byte[] digest, String sha256Header, String hmacHeader, String ed25519Header) {
        if (sha256Header == null || sha256Header.isBlank()) {
            return required ? Result.fail("missing X-Content-SHA256 header") : Result.pass("no integrity headers (not required)");
        }
        if (!constantTimeEquals(sha256Header.trim().toLowerCase(), toHex(digest))) {
            return Result.fail("SHA-256 mismatch (corruption or tampering)");
        }

        boolean authenticated = false;

        if (ed25519Pub != null) {
            if (ed25519Header == null || ed25519Header.isBlank()) {
                if (required) return Result.fail("Ed25519 key pinned but signature header missing");
            } else {
                try {
                    Signature sig = Signature.getInstance(ED25519);
                    sig.initVerify(ed25519Pub);
                    sig.update(digest);
                    if (!sig.verify(Base64.getDecoder().decode(ed25519Header.trim()))) {
                        return Result.fail("Ed25519 signature invalid");
                    }
                    authenticated = true;
                } catch (GeneralSecurityException | IllegalArgumentException e) {
                    return Result.fail("Ed25519 verify error: " + e.getMessage());
                }
            }
        }

        if (hmacKey != null) {
            if (hmacHeader == null || hmacHeader.isBlank()) {
                if (required && !authenticated) return Result.fail("HMAC secret set but header missing");
            } else {
                try {
                    Mac mac = Mac.getInstance(HMAC_ALG);
                    mac.init(hmacKey);
                    if (!constantTimeEquals(toHex(mac.doFinal(digest)), hmacHeader.trim().toLowerCase())) {
                        return Result.fail("HMAC mismatch");
                    }
                    authenticated = true;
                } catch (GeneralSecurityException e) {
                    return Result.fail("HMAC verify error: " + e.getMessage());
                }
            }
        }

        if (required && !authenticated) {
            return Result.fail("no authenticated signature available (SHA-256 alone is not authentication)");
        }
        return Result.pass(authenticated ? "signature verified" : "sha-256 verified (unauthenticated)");
    }

    public static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = Files.newInputStream(file)) {
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String resolve(String propKey, String envKey, String fallback) {
        String p = System.getProperty(propKey);
        if (p != null && !p.isBlank()) return p.trim();
        String e = System.getenv(envKey);
        if (e != null && !e.isBlank()) return e.trim();
        return fallback;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
