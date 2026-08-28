package dev.sweety.versioning.server.store;

import dev.sweety.versioning.server.security.ArtifactSigner;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight {@link DownloadSession}s by download token, shared between the HTTP download
 * adapter (producer) and the Netty integrity-transaction adapter (consumer). Sessions live only for
 * the duration of a transfer: {@link #open} on stream start, {@link #close} in a {@code finally}.
 */
public final class DownloadSessionRegistry {

    private final ConcurrentHashMap<String, DownloadSession> sessions = new ConcurrentHashMap<>();
    private final ArtifactSigner signer;

    public DownloadSessionRegistry(ArtifactSigner signer) {
        this.signer = signer;
    }

    public DownloadSession open(String token, long totalBytes) {
        DownloadSession session = new DownloadSession(totalBytes, signer);
        sessions.put(token, session);
        return session;
    }

    public void close(String token) {
        sessions.remove(token);
    }

    public Optional<DownloadSession.Snapshot> snapshot(String token) {
        DownloadSession session = token == null ? null : sessions.get(token);
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }
}
