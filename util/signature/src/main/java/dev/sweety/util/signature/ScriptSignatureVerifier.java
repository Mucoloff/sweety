package dev.sweety.util.signature;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Client-side verification for the shop/community script channel ({@code .luce.kts}).
 *
 * <p>Scripts are trusted-tier content — they compile and run with full JVM rights (see
 * {@code ScriptCompiler}), so an unsigned or tampered script must never reach the Kotlin scripting
 * host. This mirrors {@code dev.sweety.versioning.security.ArtifactVerifier}'s layering (SHA-256
 * digest, then an Ed25519 signature over that digest verified with a pinned public key) but is a
 * standalone raw-bytes verifier — scripts are not jars, so {@link Signature} (the jar/manifest
 * watermarking tool in this same module) does not apply here.
 *
 * <p>Fails closed: {@link #verify(byte[], String)} returns a failing {@link Result} whenever no
 * usable public key was configured, the signature is missing, malformed, or does not match — callers
 * must treat any non-passing result as "refuse to compile", never as "skip verification".
 */
public final class ScriptSignatureVerifier {

    private static final String ED25519 = "Ed25519";

    /** Outcome of a verification attempt; never throws for expected failure modes. */
    public record Result(boolean ok, String reason) {
        static Result pass(String reason) {
            return new Result(true, reason);
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    private final PublicKey publicKey; // nullable — absent means "cannot verify", not "trust anyway"

    private ScriptSignatureVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * @param ed25519PublicKeyBase64 X.509/SPKI public key, base64 (PEM headers tolerated and
     *                               stripped); blank/null yields a verifier that fails every check
     */
    public static ScriptSignatureVerifier of(String ed25519PublicKeyBase64) {
        if (ed25519PublicKeyBase64 == null || ed25519PublicKeyBase64.isBlank()) {
            return new ScriptSignatureVerifier(null);
        }
        try {
            String b64 = ed25519PublicKeyBase64
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(b64);
            PublicKey pub = KeyFactory.getInstance(ED25519).generatePublic(new X509EncodedKeySpec(der));
            return new ScriptSignatureVerifier(pub);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Ed25519 public key parse failed for script signature verifier", e);
        }
    }

    public boolean hasKey() {
        return publicKey != null;
    }

    /**
     * Verifies {@code scriptBytes} (the raw, unmodified {@code .luce.kts} source) against a
     * detached base64 Ed25519 signature computed server-side over the SHA-256 digest of those same
     * bytes.
     */
    public Result verify(byte[] scriptBytes, String signatureBase64) {
        if (publicKey == null) {
            return Result.fail("no Ed25519 public key configured — cannot verify");
        }
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return Result.fail("missing signature");
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureBase64.trim());
        } catch (IllegalArgumentException e) {
            return Result.fail("signature is not valid base64");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scriptBytes);
            java.security.Signature sig = java.security.Signature.getInstance(ED25519);
            sig.initVerify(publicKey);
            sig.update(digest);
            if (!sig.verify(signatureBytes)) {
                return Result.fail("Ed25519 signature invalid");
            }
            return Result.pass("signature verified");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256/Ed25519 unavailable on this JVM", e);
        } catch (GeneralSecurityException e) {
            return Result.fail("Ed25519 verify error: " + e.getMessage());
        }
    }
}
