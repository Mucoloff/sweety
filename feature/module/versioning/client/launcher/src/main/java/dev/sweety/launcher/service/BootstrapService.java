package dev.sweety.launcher.service;

import dev.sweety.launcher.net.NettyUpdaterClient;
import dev.sweety.launcher.LauncherConfig;
import dev.sweety.launcher.service.BootstrapUseCase;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application service that bootstraps the launcher: starts the updater client
 * and (optionally) the managed app process.
 */
public class BootstrapService implements BootstrapUseCase {

    private final AtomicReference<LauncherConfig> config;
    private final ApplyUpdateService applyUpdateService;
    private final Map<Artifact, Path> artifacts;
    private final Path configFile;

    public BootstrapService(
            AtomicReference<LauncherConfig> config,
            ApplyUpdateService applyUpdateService,
            Map<Artifact, Path> artifacts,
            Path configFile) {
        this.config = config;
        this.applyUpdateService = applyUpdateService;
        this.artifacts = artifacts;
        this.configFile = configFile;
    }

    @Override
    public void bootstrap(String[] args) throws Exception {
        final Runnable save = () -> config.get().save(configFile);

        final NettyUpdaterClient updater = new NettyUpdaterClient(
                config,
                PacketRegistry.REGISTRY,
                applyUpdateService,
                save);

        applyUpdateService.setIntegrityProbe(updater);

        Path appJar = artifacts.get(Artifact.APP);

        if (appJar != null && Files.exists(appJar)) {
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
