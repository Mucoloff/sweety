package dev.sweety.launcher;

import dev.sweety.launcher.patch.JarPatchApplier;
import dev.sweety.launcher.patch.PatchApplierPort;
import dev.sweety.launcher.service.ApplyUpdateService;
import dev.sweety.launcher.service.BootstrapService;
import dev.sweety.patch.applier.PatchApplier;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.security.ArtifactVerifier;
import dev.sweety.versioning.version.artifact.Artifact;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Launcher process entry point. Wires {@link BootstrapService} with its collaborators and hands off
 * to {@code bootstrap(args)} — everything below this class is already unit-testable in isolation
 * (constructor-injected), this is purely the composition root a jar's {@code Main-Class} needs.
 */
public final class LauncherMain {

    private LauncherMain() {}

    public static void main(String[] args) throws Exception {
        Path configFile = Path.of(args.length > 0 ? args[0] : "launcher-config.json");
        AtomicReference<LauncherConfig> config = new AtomicReference<>(LauncherConfig.load(configFile));

        Map<Artifact, Path> artifacts = Map.of(
                Artifact.APP, Path.of("app.jar"),
                Artifact.LAUNCHER, launcherJarPath());

        PatchApplierPort patchApplier = new JarPatchApplier(new PatchApplier(PatchTypes.PATCH_JAR, new Sha256Hash()));
        ArtifactVerifier verifier = ArtifactVerifier.fromConfig();

        ApplyUpdateService applyUpdateService = new ApplyUpdateService(
                config, artifacts, patchApplier, state -> {}, verifier);

        BootstrapService bootstrap = new BootstrapService(config, applyUpdateService, artifacts, configFile);
        bootstrap.bootstrap(args);
    }

    private static Path launcherJarPath() {
        try {
            return Path.of(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot resolve launcher jar location", e);
        }
    }
}
