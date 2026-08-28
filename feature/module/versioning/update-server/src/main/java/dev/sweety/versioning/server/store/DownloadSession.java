package dev.sweety.versioning.server.store;

import dev.sweety.versioning.server.security.ArtifactSigner;
import dev.sweety.versioning.server.util.http.BandwidthLimiter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Live integrity state of one in-flight download, keyed by download token. The HTTP streaming thread
 * feeds bytes via {@link #sink()} as it writes them; a Netty thread can concurrently call
 * {@link #snapshot()} to learn the rolling SHA-256 of the bytes served <em>so far</em> — letting the
 * client verify progressively, mid-transfer, over the already-authenticated Netty channel.
 *
 * <p>{@link MessageDigest} is not thread-safe, so all mutation and snapshotting is guarded by the
 * intrinsic lock. Chunks are coarse (tens of KB), so contention with the producer is negligible.
 */
public final class DownloadSession {

    /** Immutable view of progress at a point in time. {@code rollingSha256} hashes only bytes served so far. */
    public record Snapshot(long bytesHashed, long totalBytes, byte[] rollingSha256,
                           boolean complete, String hmacHex, String ed25519Base64) {}

    private final long totalBytes;
    private final ArtifactSigner signer;
    private final MessageDigest digest;
    private long bytesHashed;

    DownloadSession(long totalBytes, ArtifactSigner signer) {
        this.totalBytes = totalBytes;
        this.signer = signer;
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Sink handed to {@link BandwidthLimiter#transfer}; updates the rolling digest under lock. */
    public BandwidthLimiter.ChunkSink sink() {
        return (buf, off, len) -> {
            synchronized (this) {
                digest.update(buf, off, len);
                bytesHashed += len;
            }
        };
    }

    /**
     * Snapshots progress: clones the digest and finalizes a copy so the running hash is preserved.
     * When all bytes are served, also computes the HMAC/Ed25519 tags over the (now final) digest.
     */
    public synchronized Snapshot snapshot() {
        byte[] rolling;
        try {
            rolling = ((MessageDigest) digest.clone()).digest();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("SHA-256 digest not cloneable", e);
        }
        boolean complete = totalBytes >= 0 && bytesHashed >= totalBytes;
        String hmac = complete ? signer.hmacHex(rolling) : null;
        String ed = complete ? signer.ed25519Base64(rolling) : null;
        return new Snapshot(bytesHashed, totalBytes, rolling, complete, hmac, ed);
    }
}
