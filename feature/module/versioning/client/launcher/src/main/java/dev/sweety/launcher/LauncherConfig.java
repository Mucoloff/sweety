package dev.sweety.launcher;

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
import dev.sweety.util.logger.SimpleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
                             boolean autoUpdateEnabled,
                             String ed25519PublicKey,
                             boolean verifyIntegrity) {

    private static final SimpleLogger logger = SimpleLogger.of(LauncherConfig.class);

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
                true,
                "",
                true);
    }

    public void save(Path file) {
        try {
            save(file, this);
        } catch (IOException e) {
            logger.profile("save").error("Failed to save config: " + e.getMessage());
        }
    }

    public static LauncherConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            LauncherConfig def = defaults();
            save(file, def);
            return def;
        }

        final String configJson;
        try (InputStream in = Files.newInputStream(file)) {
            configJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject root = Utils.gson().fromJson(configJson, JsonObject.class);

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

        String ed25519PublicKey = root.has("ed25519PublicKey") && !root.get("ed25519PublicKey").isJsonNull()
                ? root.get("ed25519PublicKey").getAsString() : "";
        boolean verifyIntegrity = !root.has("verifyIntegrity") || root.get("verifyIntegrity").getAsBoolean();

        LauncherConfig loaded = new LauncherConfig(url, host, port, buildId, clientId, versions, channel, autoUpdate, ed25519PublicKey, verifyIntegrity);

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
        root.addProperty("ed25519PublicKey", config.ed25519PublicKey == null ? "" : config.ed25519PublicKey);
        root.addProperty("verifyIntegrity", config.verifyIntegrity);


        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmpFile)) {
            os.write(Utils.gson().toJson(root).getBytes(StandardCharsets.UTF_8));
        }

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
        return new LauncherConfig(url, host, port, buildId, clientId, newVersions, channel, autoUpdateEnabled, ed25519PublicKey, verifyIntegrity);
    }

    public LauncherConfig with(Map<Artifact, Version> versions) {
        return new LauncherConfig(url, host, port, buildId, clientId, new HashMap<>(versions), channel, autoUpdateEnabled, ed25519PublicKey, verifyIntegrity);
    }

    public LauncherInfo info() {
        String secret = System.getenv().getOrDefault("LUCE_HANDSHAKE_SECRET", "");
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

        return new LauncherConfig(serverUrl, nettyHost, nettyPort, buildId, clientId, versions, loaded.channel(), loaded.autoUpdateEnabled(),
                loaded.ed25519PublicKey() == null ? "" : loaded.ed25519PublicKey(), loaded.verifyIntegrity());
    }

}
