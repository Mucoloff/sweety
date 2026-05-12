package dev.sweety.launcher;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.launcher.update.UpdateManager;
import dev.sweety.launcher.update.UpdaterClient;
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

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        final Path configFile = Path.of("config.json");
        final Path appJar = Path.of("app.jar");
        final Path selfJar = Path.of("launcher.jar");

        final AtomicReference<LauncherConfig> config = new AtomicReference<>(LauncherConfig.load(configFile));
        final Runnable save = () -> config.get().save(configFile);

        final PatchApplier applier = new PatchApplier(PatchTypes.BIN, new Sha256Hash());

        Map<Artifact, Path> artifacts = new HashMap<>();
        artifacts.put(Artifact.APP, appJar);
        artifacts.put(Artifact.LAUNCHER, selfJar);

        final UpdateManager updateManager = new UpdateManager(config, artifacts, applier, state -> {
            if (state == State.UPDATED) save.run();
        });

        final UpdaterClient updater = new UpdaterClient(config, PacketRegistry.REGISTRY, updateManager, save);

        if (Files.exists(appJar)) {
            new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", appJar.toAbsolutePath().toString())
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
