package dev.sweety.launcher.config;

import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.Version;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public record LauncherConfig(String serverUrl,
                             String nettyHost,
                             int nettyPort,
                             UUID clientId, Version launcher, Version app,
                             boolean autoUpdateEnabled) {

    public static LauncherConfig defaults() {
        return new LauncherConfig(
                "http://localhost:8080",
                "localhost",
                9900,
                UUID.nameUUIDFromBytes("TEST".getBytes(StandardCharsets.UTF_8)),
                Version.ZERO,
                Version.ZERO,
                true);
    }

    public void save(Path file) {
        try {
            save(file,this);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public static LauncherConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            LauncherConfig def = defaults();
            save(file, def);
            return def;
        }
        LauncherConfig loaded = Utils.GSON.fromJson(Files.readString(file), LauncherConfig.class);
        return normalize(loaded);
    }

    public static void save(Path file, LauncherConfig config) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, Utils.GSON.toJson(config));
    }

    public LauncherConfig withVersions(Version launcherVersion, Version appVersion) {
        return new LauncherConfig(serverUrl, nettyHost, nettyPort, clientId, launcherVersion, appVersion, autoUpdateEnabled);
    }

    public LauncherConfig withLauncher(Version launcher) {
        return new LauncherConfig(serverUrl, nettyHost, nettyPort, clientId, launcher, app, autoUpdateEnabled);
    }

    public LauncherConfig withApp(Version app) {
        return new LauncherConfig(serverUrl, nettyHost, nettyPort, clientId, launcher, app, autoUpdateEnabled);
    }

    public LauncherInfo info() {
        return new LauncherInfo(clientId, launcher, app);
    }

    private static LauncherConfig normalize(LauncherConfig loaded) {
        final LauncherConfig def = defaults();
        if (loaded == null) return def;

        String serverUrl = loaded.serverUrl() == null || loaded.serverUrl().isBlank() ? def.serverUrl() : loaded.serverUrl();
        String nettyHost = loaded.nettyHost() == null || loaded.nettyHost().isBlank() ? def.nettyHost() : loaded.nettyHost();
        int nettyPort = loaded.nettyPort <= 0 || loaded.nettyPort > 65535 ? def.nettyPort : loaded.nettyPort;
        UUID clientId = loaded.clientId() == null ? def.clientId() : loaded.clientId();
        Version launcherVersion = loaded.launcher() == null ? def.launcher() : loaded.launcher();
        Version appVersion = loaded.app() == null ? def.app() : loaded.app();

        return new LauncherConfig(serverUrl, nettyHost, nettyPort, clientId, launcherVersion, appVersion, loaded.autoUpdateEnabled());
    }


}
