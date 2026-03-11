package dev.sweety.launcher.config;

import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.Version;

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
                UUID.fromString("TEST"),
                Version.ZERO,
                Version.ZERO,
                true);
    }

    public static LauncherConfig load(Path file) throws Exception {
        if (!Files.exists(file)) {
            LauncherConfig def = defaults();
            save(file, def);
            return def;
        }
        LauncherConfig loaded = Utils.GSON.fromJson(Files.readString(file), LauncherConfig.class);
        return normalize(loaded);
    }

    public static void save(Path file, LauncherConfig config) throws Exception {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, Utils.GSON.toJson(config));
    }

    public LauncherConfig withVersions(Version launcherVersion, Version appVersion) {
        return new LauncherConfig(serverUrl, nettyHost, nettyPort, clientId, launcherVersion, appVersion, autoUpdateEnabled);
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
