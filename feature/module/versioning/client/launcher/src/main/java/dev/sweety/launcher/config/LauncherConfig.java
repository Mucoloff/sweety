package dev.sweety.launcher.config;

import com.google.gson.JsonObject;
import dev.sweety.build.BuildInfo;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public record LauncherConfig(String url,
                             String host,
                             int port,
                             UUID buildId,
                             UUID clientId,
                             EnumMap<Artifact, Version> versions,
                             Channel channel,
                             boolean autoUpdateEnabled) {

    public static LauncherConfig defaults() {
        EnumMap<Artifact, Version> versions = new EnumMap<>(Artifact.class);

        for (Artifact artifact : Artifact.values()) {
            versions.put(artifact, Version.ZERO);
        }

        versions.put(Artifact.LAUNCHER, Version.parse(BuildInfo.VERSION));

        return new LauncherConfig(
                "http://localhost:8080",
                "localhost",
                9900,
                UUID.nameUUIDFromBytes(BuildInfo.BUILD_ID.getBytes(StandardCharsets.UTF_8)),
                UUID.nameUUIDFromBytes(BuildInfo.CLIENT_ID.getBytes(StandardCharsets.UTF_8)),
                versions,
                Channel.valueOf(BuildInfo.CHANNEL.toUpperCase()),
                true);
    }

    public void save(Path file) {
        try {
            save(file, this);
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

        JsonObject root = Utils.GSON.fromJson(
                Files.readString(file),
                JsonObject.class
        );

        String url = root.get("url").getAsString();
        String host = root.get("host").getAsString();
        int port = root.get("port").getAsInt();
        //String uuid = root.get("clientId").getAsString();

        UUID buildId;

        try {
            buildId = Utils.parseUuid(BuildInfo.BUILD_ID);
        } catch (IllegalArgumentException e) {
            buildId = UUID.nameUUIDFromBytes(BuildInfo.BUILD_ID.getBytes(StandardCharsets.UTF_8));
        }

        UUID clientId;
        try {
            clientId = Utils.parseUuid(BuildInfo.CLIENT_ID);
        } catch (IllegalArgumentException e) {
            clientId = UUID.nameUUIDFromBytes(BuildInfo.CLIENT_ID.getBytes(StandardCharsets.UTF_8));
        }


        EnumMap<Artifact, Version> versions = new EnumMap<>(Artifact.class);
        JsonObject versionsJson = root.getAsJsonObject("versions");
        for (Artifact artifact : Artifact.values()) {
            final Version ver;
            final String artifactName = artifact.prettyName();
            if (versionsJson.has(artifactName)) {
                String versionStr = versionsJson.get(artifactName).getAsString();
                ver = Version.parse(versionStr);
            } else ver = Version.ZERO;

            versions.put(artifact, ver);
        }

        versions.put(Artifact.LAUNCHER, Version.parse(BuildInfo.VERSION));

        //String chan = root.get("channel").getAsString();
        Channel channel = Channel.valueOf(BuildInfo.CHANNEL.toUpperCase());
        boolean autoUpdate = root.get("autoUpdate").getAsBoolean();

        LauncherConfig loaded = new LauncherConfig(url, host, port, buildId, clientId, versions, channel, autoUpdate);

        return normalize(loaded);
    }

    public static void save(Path file, LauncherConfig config) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }

        JsonObject root = new JsonObject();

        root.addProperty("url", config.url);
        root.addProperty("host", config.host);
        root.addProperty("port", config.port);
        //root.addProperty("clientId", config.clientId.toString());

        JsonObject versions = new JsonObject();
        for (Map.Entry<Artifact, Version> entry : config.versions.entrySet()) {
            versions.addProperty(entry.getKey().prettyName(), entry.getValue().toString());
        }

        versions.remove("launcher");

        root.add("versions", versions);

        //root.addProperty("channel", config.channel.prettyName());
        root.addProperty("autoUpdate", config.autoUpdateEnabled);


        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmpFile, Utils.GSON.toJson(root));

        Files.move(
                tmpFile,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }


    public LauncherConfig with(Artifact artifact, Version version) {
        versions.put(artifact, version);
        return new LauncherConfig(url, host, port, buildId, clientId, versions, channel, autoUpdateEnabled);
    }

    public LauncherConfig with(EnumMap<Artifact, Version> versions) {
        return new LauncherConfig(url, host, port, buildId, clientId, versions, channel, autoUpdateEnabled);
    }

    public LauncherInfo info() {
        return new LauncherInfo(buildId, clientId, versions, channel);
    }

    private static LauncherConfig normalize(LauncherConfig loaded) {
        final LauncherConfig def = defaults();
        if (loaded == null) return def;

        String serverUrl = loaded.url() == null || loaded.url().isBlank() ? def.url() : loaded.url();
        String nettyHost = loaded.host() == null || loaded.host().isBlank() ? def.host() : loaded.host();
        int nettyPort = loaded.port <= 0 || loaded.port > 65535 ? def.port : loaded.port;
        UUID buildId = loaded.buildId() == null ? def.buildId() : loaded.buildId();
        UUID clientId = loaded.clientId() == null ? def.clientId() : loaded.clientId();

        EnumMap<Artifact, Version> versions = new EnumMap<>(Artifact.class);

        for (Artifact artifact : Artifact.values()) {
            versions.put(artifact, loaded.versions().getOrDefault(artifact, def.versions().getOrDefault(artifact, Version.ZERO)));
        }

        return new LauncherConfig(serverUrl, nettyHost, nettyPort, buildId, clientId, versions, loaded.channel(), loaded.autoUpdateEnabled());
    }

}
