package dev.sweety.launcher;

import dev.sweety.extension.versioning.UpdateableExtensionManager;
import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.launcher.extension.LauncherExtension;
import dev.sweety.launcher.update.*;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.patch.applier.PatchApplier;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SweetyLauncher {

    private final Path configFile;
    private final AtomicReference<LauncherConfig> config;
    private final UpdateManager updateManager;
    private final UpdaterClient updater;
    private final UpdateableExtensionManager<LauncherExtension> extensionManager;

    private Consumer<State> handshakeListener;

    public SweetyLauncher(Path configFile, Map<Artifact, Path> initialArtifacts) throws IOException {
        this.configFile = configFile;
        this.config = new AtomicReference<>(LauncherConfig.load(configFile));
        PatchApplier applier = new PatchApplier(PatchTypes.BIN, new Sha256Hash());
        
        this.updateManager = new UpdateManager(config, initialArtifacts, applier, state -> {
            if (handshakeListener != null) handshakeListener.accept(state);
        });

        this.updater = new UpdaterClient(config, PacketRegistry.REGISTRY, updateManager, this::saveConfig);
        
        this.extensionManager = new UpdateableExtensionManager<>(new File("."), LauncherExtension.class);
    }

    public void setHandshakeListener(Consumer<State> handshakeListener) {
        this.handshakeListener = handshakeListener;
    }

    public void start() {
        this.extensionManager.load();
        this.extensionManager.extensions().values().forEach(ext -> ext.init(this));
        this.extensionManager.extensions().values().forEach(LauncherExtension::onInitialize);
        
        Messenger.init(updater);
    }

    public void shutdown() {
        this.extensionManager.shutdown();
        updater.stop();
        saveConfig();
    }

    public void saveConfig() {
        config.get().save(configFile);
    }

    public LauncherConfig getConfig() {
        return config.get();
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public void launchApp(Path appJar) throws IOException {
        if (!appJar.toFile().exists()) {
            System.err.println("App JAR not found: " + appJar);
            return;
        }

        new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", appJar.toAbsolutePath().toString())
                .inheritIO()
                .start()
                .onExit().thenRun(this::shutdown);
    }
}
