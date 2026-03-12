package dev.sweety.launcher.recode;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.launcher.update.UpdateManager;
import dev.sweety.launcher.update.UpdaterClient;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.State;

import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        final Path configFile = Path.of("config.json");
        final Path appJar = Path.of("dest-app.jar"); //todo
        final Path selfJar = Path.of("dest-launcher.jar");

        final AtomicReference<LauncherConfig> configRef = new AtomicReference<>(LauncherConfig.load(configFile));


        final CompletableFuture<State> handshake = new CompletableFuture<>();

        final Runnable stop = () -> {

        };

        LauncherConfig config = configRef.get();
        final UpdateManager updateManager = new UpdateManager(config.serverUrl(), config.clientId(), appJar, selfJar, handshake);
        final UpdaterClient updater = new UpdaterClient(config.nettyHost(), config.nettyPort(), PacketRegistry.REGISTRY, config.info(), updateManager, stop);

        handshake.exceptionally(throwable -> {
            System.err.println("Failed to complete handshake: " + throwable.getMessage());
            return null;
        }).thenAccept(state -> {
            if (state == null) {
                System.err.println("Handshake failed with unknown error.");
                return;
            }
            switch (state) {
                case UNAVAILABLE ->
                        System.out.println("Update server is currently unavailable. Please try again later.");
                case UP_TO_DATE -> System.out.println("Your launcher and app are up to date!");

                default -> {
                    System.out.println("state: " + state);



                }
            }
        });


        Messenger.init(updater);
    }

}
