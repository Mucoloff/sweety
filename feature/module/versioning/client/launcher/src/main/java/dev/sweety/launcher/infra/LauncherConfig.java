package dev.sweety.launcher.infra;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.build.BuildInfo;
import dev.sweety.versioning.security.HandshakeProof;
import dev.sweety.data.ObjectUtils;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record LauncherConfig(String url,
                             String host,
                             int port,
                             UUID buildId,
                             UUID clientId,
                             Map<Artifact, Version> versions,
                             Channel channel,
                             boolean autoUpdateEnabled) {

    public static LauncherConfig defaults() {
        Map<Artifact, Version> versions = new HashMap<>();

        versions.put(Artifact.APP, Version.ZERO);
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

        JsonObject root = Utils.gson().fromJson(
                Files.readString(file),
                JsonObject.class
        );

        String url = root.get("url").getAsString();
        String host = root.get("host").getAsString();
        int port = root.get("port").getAsInt();

        UUID buildId;
        try {
            buildId = ObjectUtils.parseUuid(BuildInfo.BUILD_ID);
        } catch (IllegalArgumentException e) {
            buildId = UUID.nameUUIDFromBytes(BuildInfo.BUILD_ID.getBytes(StandardCharsets.UTF_8));
        }

        UUID clientId;
        try {
            clientId = ObjectUtils.parseUuid(BuildInfo.CLIENT_ID);
        } catch (IllegalArgumentException e) {
            clientId = UUID.nameUUIDFromBytes(BuildInfo.CLIENT_ID.getBytes(StandardCharsets.UTF_8));
        }


        Map<Artifact, Version> versions = new HashMap<>();
        JsonObject versionsJson = root.getAsJsonObject("versions");
        if (versionsJson != null) {
            for (Map.Entry<String, JsonElement> entry : versionsJson.entrySet()) {
                versions.put(new Artifact(entry.getKey().toUpperCase()), Version.parse(entry.getValue().getAsString()));
            }
        }

        // Always ensure current launcher version is set from build info
        versions.put(Artifact.LAUNCHER, Version.parse(BuildInfo.VERSION));

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

        JsonObject versions = new JsonObject();
        for (Map.Entry<Artifact, Version> entry : config.versions.entrySet()) {
            // Don't save launcher version to config file as it's provided by build info
            if (entry.getKey().equals(Artifact.LAUNCHER)) continue;
            versions.addProperty(entry.getKey().name().toLowerCase(), entry.getValue().toString());
        }

        root.add("versions", versions);
        root.addProperty("autoUpdate", config.autoUpdateEnabled);


        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmpFile, Utils.gson().toJson(root));

        Files.move(
                tmpFile,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }


    public LauncherConfig with(Artifact artifact, Version version) {
        Map<Artifact, Version> newVersions = new HashMap<>(this.versions);
        newVersions.put(artifact, version);
        return new LauncherConfig(url, host, port, buildId, clientId, newVersions, channel, autoUpdateEnabled);
    }

    public LauncherConfig with(Map<Artifact, Version> versions) {
        return new LauncherConfig(url, host, port, buildId, clientId, new HashMap<>(versions), channel, autoUpdateEnabled);
    }

    public LauncherInfo info() {
        String secret = System.getenv().getOrDefault("SWEETY_HANDSHAKE_SECRET", "");
        byte[] proof = HandshakeProof.compute(secret, buildId, clientId, versions, channel);
        return new LauncherInfo(buildId, clientId, versions, channel, proof);
    }

    private static LauncherConfig normalize(LauncherConfig loaded) {
        final LauncherConfig def = defaults();
        if (loaded == null) return def;

        String serverUrl = loaded.url() == null || loaded.url().isBlank() ? def.url() : loaded.url();
        String nettyHost = loaded.host() == null || loaded.host().isBlank() ? def.host() : loaded.host();
        int nettyPort = loaded.port <= 0 || loaded.port > 65535 ? def.port : loaded.port;
        UUID buildId = loaded.buildId() == null ? def.buildId() : loaded.buildId();
        UUID clientId = loaded.clientId() == null ? def.clientId() : loaded.clientId();

        Map<Artifact, Version> versions = new HashMap<>(loaded.versions());
        if (!versions.containsKey(Artifact.APP)) {
            versions.put(Artifact.APP, Version.ZERO);
        }

        return new LauncherConfig(serverUrl, nettyHost, nettyPort, buildId, clientId, versions, loaded.channel(), loaded.autoUpdateEnabled());
    }

}
