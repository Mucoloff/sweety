package dev.sweety.launcher;

import dev.sweety.launcher.patch.JarPatchApplier;
import dev.sweety.launcher.patch.PatchApplierPort;
import dev.sweety.launcher.service.ApplyUpdateService;
import dev.sweety.launcher.service.BootstrapService;
import dev.sweety.patch.applier.PatchApplier;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.lifecycle.Lifecycle;
import dev.sweety.versioning.security.ArtifactVerifier;
import dev.sweety.versioning.version.artifact.Artifact;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Modern Bootstrap skeleton implementing {@link Lifecycle}.
 * Loads, verifies, and executes application modules in-memory without unencrypted disk leaks.
 */
public final class Bootstrap implements Lifecycle {

    private final String[] args;
    private Path configFile;
    private AtomicReference<LauncherConfig> config;
    private BootstrapService bootstrapService;

    public Bootstrap(String[] args) {
        this.args = args != null ? args : new String[0];
    }

    @Override
    public void load() throws Exception {
        this.configFile = Path.of(args.length > 0 ? args[0] : "launcher-config.json");
        this.config = new AtomicReference<>(LauncherConfig.load(configFile));

        Map<Artifact, Path> artifacts = Map.of(
                Artifact.APP, Path.of("app.jar"),
                Artifact.LAUNCHER, launcherJarPath());

        PatchApplierPort patchApplier = new JarPatchApplier(new PatchApplier(PatchTypes.PATCH_JAR, new Sha256Hash()));
        ArtifactVerifier verifier = ArtifactVerifier.fromConfig();

        ApplyUpdateService applyUpdateService = new ApplyUpdateService(
                config, artifacts, patchApplier, state -> {}, verifier);

        this.bootstrapService = new BootstrapService(config, applyUpdateService, artifacts, configFile);
    }

    @Override
    public void start() throws Exception {
        if (bootstrapService != null) {
            bootstrapService.bootstrap(args);
        }
    }

    @Override
    public void shutdown() {
        // Cleanup resources
    }

    public static void main(String[] args) throws Exception {
        Bootstrap bootstrap = new Bootstrap(args);
        bootstrap.load();
        bootstrap.start();
    }

    private static Path launcherJarPath() {
        try {
            return Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot resolve launcher jar location", e);
        }
    }
}
