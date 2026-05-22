package dev.sweety.launcher.adapter.in.cli;

import dev.sweety.launcher.adapter.out.patch.JarPatchApplier;
import dev.sweety.launcher.application.update.ApplyUpdateService;
import dev.sweety.launcher.infra.LauncherConfig;
import dev.sweety.launcher.port.in.ApplyUpdateUseCase;
import dev.sweety.launcher.adapter.out.netty.NettyUpdaterClient;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.patch.applier.PatchApplier;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CLI entry-point for the launcher (hexagonal adapter/in/cli).
 * Wires together infra, application services, and adapters.
 */
public class LauncherMain {

    public static void main(String[] args) throws Exception {
        final Path configFile = Path.of("config.json");
        final Path appJar = Path.of("app.jar");
        final Path selfJar = Path.of("launcher.jar");

        final AtomicReference<LauncherConfig> config = new AtomicReference<>(LauncherConfig.load(configFile));
        final Runnable save = () -> config.get().save(configFile);

        final PatchApplier rawApplier = new PatchApplier(PatchTypes.PATCH_JAR, new Sha256Hash());
        final JarPatchApplier patchApplier = new JarPatchApplier(rawApplier);

        Map<Artifact, Path> artifacts = new HashMap<>();
        artifacts.put(Artifact.APP, appJar);
        artifacts.put(Artifact.LAUNCHER, selfJar);

        final ApplyUpdateService applyUpdateService = new ApplyUpdateService(
                config,
                artifacts,
                patchApplier,
                state -> {
                    if (state == State.UPDATED) save.run();
                });

        final NettyUpdaterClient updater = new NettyUpdaterClient(
                config,
                PacketRegistry.REGISTRY,
                applyUpdateService,
                save);

        if (Files.exists(appJar)) {
            new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-jar",
                    appJar.toAbsolutePath().toString())
                    .inheritIO()
                    .start()
                    .onExit().thenRun(() -> {
                        updater.stop();
                        save.run();
                        System.exit(0);
                    });
        }

        Messenger.init(updater);
    }
}
