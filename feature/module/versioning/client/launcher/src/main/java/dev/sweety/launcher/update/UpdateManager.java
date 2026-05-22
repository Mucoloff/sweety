package dev.sweety.launcher.update;

import dev.sweety.launcher.application.update.ApplyUpdateService;
import dev.sweety.launcher.domain.update.UpdatePlan;
import dev.sweety.launcher.infra.LauncherConfig;
import dev.sweety.launcher.port.in.ApplyUpdateUseCase;
import dev.sweety.launcher.port.out.PatchApplierPort;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @deprecated Use {@link ApplyUpdateService} directly.
 *             This class now implements {@link ApplyUpdateUseCase} by delegating to
 *             {@link ApplyUpdateService} and is kept for backward compatibility only.
 */
@Deprecated
public class UpdateManager implements ApplyUpdateUseCase {

    private final ApplyUpdateService delegate;

    public UpdateManager(AtomicReference<LauncherConfig> config,
                         Map<Artifact, Path> artifactPathMap,
                         PatchApplierPort patchApplier,
                         Consumer<State> handshakeState) {
        this.delegate = new ApplyUpdateService(config, artifactPathMap, patchApplier, handshakeState);
    }

    /** Legacy constructor that wraps a raw {@link dev.sweety.patch.applier.PatchApplier}. */
    public UpdateManager(AtomicReference<LauncherConfig> config,
                         Map<Artifact, Path> artifactPathMap,
                         dev.sweety.patch.applier.PatchApplier applier,
                         Consumer<State> handshakeState) {
        this(config, artifactPathMap,
                new dev.sweety.launcher.adapter.out.patch.JarPatchApplier(applier),
                handshakeState);
    }

    public void registerArtifact(Artifact artifact, Path localPath) {
        delegate.registerArtifact(artifact, localPath);
    }

    /** @deprecated Use {@link #applyUpdate(UpdatePlan)} via {@link ApplyUpdateUseCase}. */
    @Deprecated
    public void downloadUpdate(Artifact artifact, String token,
                               dev.sweety.versioning.version.Version version,
                               dev.sweety.versioning.protocol.handshake.DownloadType type) {
        delegate.applyUpdate(new UpdatePlan(artifact,
                dev.sweety.versioning.version.Version.ZERO,
                version,
                token,
                type));
    }

    @Override
    public void applyUpdate(UpdatePlan plan) {
        delegate.applyUpdate(plan);
    }

    @Override
    public void markUpToDate() {
        delegate.markUpToDate();
    }

    @Override
    public void markUnavailable() {
        delegate.markUnavailable();
    }

    /** @deprecated Use {@link #markUpToDate()} */
    @Deprecated
    public void upToDate() {
        markUpToDate();
    }

    /** @deprecated Use {@link #markUnavailable()} */
    @Deprecated
    public void unavailable() {
        markUnavailable();
    }
}
