package dev.sweety.launcher.service;

import dev.sweety.launcher.data.UpdatePlan;
import dev.sweety.launcher.LauncherConfig;
import dev.sweety.launcher.service.ApplyUpdateUseCase;
import dev.sweety.launcher.service.IntegrityProbePort;
import dev.sweety.launcher.patch.PatchApplierPort;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.security.ArtifactVerifier;
import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.protocol.integrity.IntegrityResponse;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Application service implementing {@link ApplyUpdateUseCase}.
 * Orchestrates download, patch application, and jar replacement.
 */
public class ApplyUpdateService implements ApplyUpdateUseCase {

    private static final SimpleLogger LOG = SimpleLogger.of(ApplyUpdateService.class);

    private final Map<Artifact, Path> artifactPathMap;
    private final PatchApplierPort patchApplier;
    private final Consumer<State> handshakeState;
    private final AtomicReference<LauncherConfig> config;
    private final ArtifactVerifier verifier;

    /** Set after the Netty client is built; enables the live cross-check. May stay null (HTTP-only verify). */
    private final AtomicReference<IntegrityProbePort> integrityProbe = new AtomicReference<>();

    public ApplyUpdateService(
            AtomicReference<LauncherConfig> config,
            Map<Artifact, Path> artifactPathMap,
            PatchApplierPort patchApplier,
            Consumer<State> handshakeState,
            ArtifactVerifier verifier) {
        this.config = config;
        this.artifactPathMap = new HashMap<>(artifactPathMap);
        this.patchApplier = patchApplier;
        this.handshakeState = handshakeState;
        this.verifier = verifier;

        config.getAndUpdate(cfg -> {
            final Map<Artifact, dev.sweety.versioning.version.Version> versions = new HashMap<>(cfg.versions());
            boolean edited = false;
            for (Map.Entry<Artifact, Path> entry : artifactPathMap.entrySet()) {
                Artifact artifact = entry.getKey();
                Path path = entry.getValue();
                if (!Files.exists(path)) {
                    versions.put(artifact, dev.sweety.versioning.version.Version.ZERO);
                    edited = true;
                }
            }
            if (!edited) return cfg;
            return cfg.with(versions);
        });
    }

    /** Wires the Netty integrity probe for the live progressive cross-check (optional). */
    public void setIntegrityProbe(IntegrityProbePort probe) {
        this.integrityProbe.set(probe);
    }

    public void registerArtifact(Artifact artifact, Path localPath) {
        this.artifactPathMap.put(artifact, localPath);
        config.getAndUpdate(cfg -> {
            if (!cfg.versions().containsKey(artifact)) {
                return cfg.with(artifact, dev.sweety.versioning.version.Version.ZERO);
            }
            return cfg;
        });
    }

    @Override
    public void applyUpdate(UpdatePlan plan) {
        Artifact artifact = plan.artifact();
        Path original = artifactPathMap.get(artifact);
        if (original == null) {
            LOG.error("No path registered for artifact: ", artifact, " clientId=", config.get().clientId());
            return;
        }

        Path newFile = original.resolveSibling(original.getFileName() + ".new");

        boolean isPatch = plan.downloadType() == DownloadType.PATCH;
        Path downloaded = isPatch
                ? original.resolveSibling(original.getFileName() + plan.toVersion().toString() + patchApplier.extension())
                : newFile;

        if (!downloadArtifactSafe(plan.token(), downloaded)) return;

        if (isPatch && !applyPatchSafe(original, downloaded, newFile, plan.toVersion())) return;

        if (!artifact.equals(Artifact.LAUNCHER)) replaceJarSafe(newFile, original);

        complete(State.UPDATED);
    }

    @Override
    public void markUpToDate() {
        complete(State.UP_TO_DATE);
    }

    @Override
    public void markUnavailable() {
        complete(State.UNAVAILABLE);
    }

    // ---- private helpers ------------------------------------------------

    private boolean downloadArtifactSafe(String token, Path downloaded) {
        try {
            downloadArtifact(token, downloaded);
            return true;
        } catch (Exception e) {
            complete(State.UNAVAILABLE);
            LOG.error("Download failed clientId=", config.get().clientId(), e);
            return false;
        }
    }

    private boolean applyPatchSafe(Path original, Path downloaded, Path newFile, dev.sweety.versioning.version.Version version) {
        LOG.info("Applying patch to ", original.getFileName(), " clientId=", config.get().clientId());
        Path backup = original.resolveSibling(original.getFileName() + ".bak");
        try {
            Files.copy(original, backup, REPLACE_EXISTING);
        } catch (IOException e) {
            complete(State.UNAVAILABLE);
            LOG.error("Could not create backup for patch clientId=", config.get().clientId(), e);
            return false;
        }

        try {
            patchApplier.patch(backup, newFile, downloaded.getParent(), original.getFileName() + version.toString());
            Files.deleteIfExists(downloaded);
            Files.deleteIfExists(backup);
            return true;
        } catch (IOException e) {
            try {
                Files.move(backup, original, REPLACE_EXISTING, ATOMIC_MOVE);
            } catch (IOException ex) {
                LOG.error("Failed to restore original JAR after patch failure: ", original, ex);
            }
            complete(State.UNAVAILABLE);
            LOG.error("Patch apply failed clientId=", config.get().clientId(), e);
            return false;
        }
    }

    private void replaceJarSafe(Path source, Path target) {
        if (!Files.exists(source)) return;
        try {
            Files.move(source, target, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (IOException e) {
            complete(State.UNAVAILABLE);
            LOG.error("replaceJarSafe failed clientId=", config.get().clientId(), " target=", target, e);
        }
    }

    private void complete(State state) {
        if (handshakeState != null) handshakeState.accept(state);
    }

    private void downloadArtifact(String token, Path destination) throws Exception {
        final String serverUrl = config.get().url();
        final java.util.UUID clientId = config.get().clientId();
        final String qToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        final String qClient = URLEncoder.encode(clientId.toString(), StandardCharsets.UTF_8);
        final URL downloadUrl = new URI(serverUrl + "/download?clientId=" + qClient + "&token=" + qToken).toURL();

        Exception last = new IllegalStateException("download failed without details");
        for (int attempt = 1; attempt <= 3; attempt++) {
            Path tmp = destination.resolveSibling(destination.getFileName() + ".part");
            try {
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6_000);
                conn.setReadTimeout(20_000);

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("download failed status=" + status);
                }

                final String shaHeader = conn.getHeaderField("X-Content-SHA256");
                final String hmacHeader = conn.getHeaderField("X-Content-HMAC");
                final String edHeader = conn.getHeaderField("X-Content-Ed25519");

                byte[] digest = streamWithProbe(conn, tmp, token);

                ArtifactVerifier.Result result = verifier.verify(digest, shaHeader, hmacHeader, edHeader);
                if (!result.ok()) {
                    Files.deleteIfExists(tmp);
                    throw new SecurityException("artifact integrity verification failed: " + result.reason());
                }
                LOG.info("Integrity OK (", result.reason(), ") for token=", token, " clientId=", clientId);

                Files.move(tmp, destination, REPLACE_EXISTING, ATOMIC_MOVE);
                return;
            } catch (SecurityException sec) {
                last = sec;
                LOG.error("Integrity check failed (attempt ", attempt, "/3) clientId=", clientId, sec);
                Thread.sleep(1000L * attempt);
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(1000L * attempt);
            }
        }
        throw last;
    }

    /**
     * Streams the response into {@code tmp}, computing SHA-256 in one pass. While streaming, an
     * optional monitor polls the server's rolling hash over Netty and aborts early on a byte-exact
     * prefix mismatch (mid-stream tamper detection). Returns the full SHA-256 of the received bytes.
     */
    private byte[] streamWithProbe(HttpURLConnection conn, Path tmp, String token) throws Exception {
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final AtomicLong written = new AtomicLong();
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicReference<String> tamper = new AtomicReference<>();

        Thread monitor = startProbeMonitor(token, tmp, written, done, tamper);
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                written.addAndGet(n);
                out.flush();
                String t = tamper.get();
                if (t != null) throw new SecurityException("progressive integrity mismatch: " + t);
            }
        } finally {
            done.set(true);
            if (monitor != null) monitor.interrupt();
        }
        return md.digest();
    }

    /** Spawns the Netty rolling-hash monitor, or returns {@code null} if no probe is wired. */
    private Thread startProbeMonitor(String token, Path tmp, AtomicLong written, AtomicBoolean done, AtomicReference<String> tamper) {
        final IntegrityProbePort probe = integrityProbe.get();
        if (probe == null) return null;

        Thread monitor = new Thread(() -> {
            while (!done.get()) {
                try {
                    Thread.sleep(400L);
                    IntegrityResponse resp = probe.probe(token).get(2, TimeUnit.SECONDS);
                    if (resp == null || !resp.isKnown()) continue;

                    long serverBytes = resp.getBytesHashed();
                    byte[] serverRolling = resp.getRollingSha256();
                    if (serverBytes <= 0 || serverRolling == null || serverRolling.length == 0) continue;
                    if (serverBytes > written.get()) continue; // client hasn't caught up yet

                    byte[] clientPrefix = sha256OfPrefix(tmp, serverBytes);
                    if (clientPrefix.length == 0) continue; // prefix not fully flushed; retry next round
                    if (!Arrays.equals(clientPrefix, serverRolling)) {
                        tamper.set("rolling hash differs at " + serverBytes + " bytes");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ignored) {
                    // best-effort: probe timeout / channel down — header verification remains authoritative
                }
            }
        }, "integrity-monitor");
        monitor.setDaemon(true);
        monitor.start();
        return monitor;
    }

    /** SHA-256 of the first {@code n} bytes of {@code file} (already-flushed prefix). */
    private static byte[] sha256OfPrefix(Path file, long n) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[64 * 1024];
        long remaining = n;
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while (remaining > 0 && (read = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                md.update(buf, 0, read);
                remaining -= read;
            }
        }
        if (remaining > 0) return new byte[0]; // prefix not fully on disk yet; skip this round
        return md.digest();
    }
}
