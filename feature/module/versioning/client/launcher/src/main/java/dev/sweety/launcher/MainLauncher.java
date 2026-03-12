package dev.sweety.launcher;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.launcher.update.UpdateManager;
import dev.sweety.launcher.update.UpdaterClient;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.State;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        final Path configFile = Path.of("config.json");
        final Path appJar = Path.of("dest-app.jar"); //todo
        final Path selfJar = Path.of("dest-launcher.jar");

        final AtomicReference<LauncherConfig> config = new AtomicReference<>(LauncherConfig.load(configFile));

        final Runnable save = () -> config.get().save(configFile);

        final Consumer<State> handshake = state -> {
            if (state == null) {
                System.err.println("Handshake failed with unknown error.");
                return;
            }
            switch (state) {
                case UNAVAILABLE ->
                        System.out.println("Update server is currently unavailable. Please try again later.");
                case UP_TO_DATE -> System.out.println("Your launcher and app are up to date!");
                default -> {
                    System.out.println("updated " + state.name().toLowerCase());
                    save.run();
                }
            }
        };

        final UpdateManager updateManager = new UpdateManager(config, appJar, selfJar, handshake);
        final UpdaterClient updater = new UpdaterClient(config, PacketRegistry.REGISTRY, updateManager, save);


        Messenger.init(updater);
    }

}
