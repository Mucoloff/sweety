package dev.sweety.launcher.application.update;

import dev.sweety.launcher.domain.update.UpdatePlan;
import dev.sweety.launcher.infra.LauncherConfig;
import dev.sweety.launcher.port.in.ApplyUpdateUseCase;
import dev.sweety.launcher.port.out.PatchApplierPort;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
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

    public ApplyUpdateService(
            AtomicReference<LauncherConfig> config,
            Map<Artifact, Path> artifactPathMap,
            PatchApplierPort patchApplier,
            Consumer<State> handshakeState) {
        this.config = config;
        this.artifactPathMap = new HashMap<>(artifactPathMap);
        this.patchApplier = patchApplier;
        this.handshakeState = handshakeState;

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

                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(1000L * attempt);
            }
        }
        throw last;
    }
}
