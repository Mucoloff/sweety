package dev.sweety.launcher;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.launcher.update.*;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.patch.applier.PatchApplier;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        final Path configFile = Path.of("launcher/config.json");
        final Path appJar = Path.of("launcher/dest-app.jar"); //todo
        final Path selfJar = Path.of("launcher/dest-launcher.jar");

        final AtomicReference<LauncherConfig> config = new AtomicReference<>(LauncherConfig.load(configFile));

        final Runnable save = () -> config.get().save(configFile);

        final Consumer<State> handshake = state -> {
            if (state == null) {
                System.err.println("Handshake failed with unknown error.");
                return;
            }
            switch (state) {
                case UNAVAILABLE -> System.out.println("Update server is currently unavailable. Please try again later.");
                case UP_TO_DATE -> System.out.println("You are up to date!");
                default -> {
                    System.out.println("updated");
                    save.run();
                }
            }
        };

        final PatchApplier applier = new PatchApplier(PatchTypes.BIN, new Sha256Hash());

        final UpdateManager updateManager = new UpdateManager(config, new EnumMap<>(Map.of(
                Artifact.APP, appJar,
                Artifact.LAUNCHER, selfJar
        )), applier, handshake);
        final UpdaterClient updater = new UpdaterClient(config, PacketRegistry.REGISTRY, updateManager, save);

        Messenger.init(updater);
    }

}
