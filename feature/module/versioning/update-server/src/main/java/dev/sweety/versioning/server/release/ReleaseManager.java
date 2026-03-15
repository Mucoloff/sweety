package dev.sweety.versioning.server.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.versioning.server.storage.Storage;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;

public class ReleaseManager {

    private static final int HISTORY_LIMIT = 20;

    private final EnumMap<Artifact, ReleaseState> states = new EnumMap<>(Artifact.class);

    public ReleaseManager(Storage storage) throws IOException {

        for (Artifact artifact : Artifact.values()) {
            ReleaseState state = new ReleaseState(artifact, storage);
            states.put(artifact, state);

            state.latest = loadOrDefault(state);
        }
    }

    public ReleaseInfo latest(Artifact artifact) {
        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {
            return s.latest;
        }
    }

    public void update(@NotNull Artifact artifact, @NotNull ReleaseInfo latest) {
        final ReleaseState s = states.get(artifact);

        synchronized (s.lock) {
            s.latest = latest;
        }
    }

    private void updateAll(ReleaseProvider provider) throws IOException {
        for (Artifact artifact : Artifact.values()) update(artifact, provider.provide(artifact));
    }

    private ReleaseInfo loadOrDefault(ReleaseState s) throws IOException {
        if (!Files.exists(s.metadata)) {
            ReleaseInfo def = ReleaseInfo.DEFAULT;
            persist(s, def);
            return def;
        }

        JsonObject root = Utils.GSON.fromJson(
                Files.readString(s.metadata),
                JsonObject.class
        );

        JsonObject latest = root.getAsJsonObject("latest");

        JsonArray hist = root.getAsJsonArray("history");

        if (hist != null) {
            for (JsonElement el : hist) {
                s.history.addLast(deserialize(el.getAsJsonObject()));
            }
        }

        return deserialize(latest);
    }

    private void persist(Artifact artifact) throws IOException {
        final ReleaseState s = states.get(artifact);

        synchronized (s.lock) {
            persist(s, s.latest);
        }
    }

    private void persistAll() throws IOException {
        for (Artifact artifact : Artifact.values()) persist(artifact);
    }

    private void persist(ReleaseState s, ReleaseInfo latest) throws IOException {

        JsonObject root = new JsonObject();

        root.add("latest", serialize(latest));

        JsonArray hist = new JsonArray();

        for (ReleaseInfo info : s.history) {
            hist.add(serialize(info));
        }

        root.add("history", hist);

        Path tmpFile = s.tmp.resolve("metadata.tmp");

        Files.writeString(tmpFile, Utils.GSON.toJson(root));

        Files.move(
                tmpFile,
                s.metadata,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    private JsonObject serialize(ReleaseInfo state) {
        JsonObject obj = new JsonObject();

        obj.addProperty("version", state.version().toString());
        obj.addProperty("channel", state.channel().toString());
        obj.addProperty("updatedAt", state.updatedAt().toString());

        return obj;
    }

    private ReleaseInfo deserialize(JsonObject obj) {
        return new ReleaseInfo(
                Version.parse(obj.get("version").getAsString()),
                Channel.valueOf(obj.get("channel").getAsString()),
                Instant.parse(obj.get("updatedAt").getAsString())
        );
    }

    private Path resolveFile(Path path, Artifact artifact, Version version, Channel channel, String extension) throws IOException {
        final String name = artifact.name().toLowerCase();
        final Path dir = version.resolve(path.resolve(name));
        Files.createDirectories(dir);
        return dir.resolve(name + "-" + version + "." + extension);
    }

    private Path resolveTempJar(ReleaseState s, @NotNull Artifact artifact, Version version, Channel channel) throws IOException {
        return resolveFile(s.tmp, artifact, version, channel, "jar.tmp");
    }

    public Path resolveBaseJar(@NotNull Artifact artifact, Version version, Channel channel) throws IOException {
        final ReleaseState s = states.get(artifact);
        synchronized (s.lock) {
            return resolveBaseJar(s, artifact, version, channel);
        }
    }

    public Path resolveBaseJar(ReleaseState s, @NotNull Artifact artifact, Version version, Channel channel) throws IOException {
        return resolveFile(s.base, artifact, version, channel, "jar");
    }

    public ReleaseInfo rollback(Artifact artifact) throws IOException {

        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {

            ReleaseInfo prev = s.history.pollFirst();

            if (prev == null)
                return null;

            s.latest = prev;

            persist(s, prev);

            return prev;
        }
    }

    public ReleaseInfo applyRelease(
            Artifact artifact,
            String channel,
            String version,
            byte[] jar
    ) throws IOException {


        Channel ch;
        try {
            ch = Channel.valueOf(channel);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid channel: " + channel, e);
        }


        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {

            Version ver = null;

            if (version != null) {
                if (jar == null)
                    throw new IllegalArgumentException(artifact + ".jar missing");

                ver = Version.parse(version);
                writeJar(s, artifact, ver, ch, jar);
            }

            ReleaseInfo current = s.latest;

            Version nextVer = ver != null ? ver : current.version();

            ReleaseInfo next = new ReleaseInfo(nextVer, ch);

            if (Objects.equals(next, current))
                return null;

            s.history.addFirst(current);

            while (s.history.size() > HISTORY_LIMIT)
                s.history.removeLast();

            s.latest = next;

            persist(s, next);

            return s.latest;
        }
    }

    private void writeJar(
            ReleaseState s,
            Artifact artifact,
            Version version,
            Channel channel,
            byte[] data
    ) throws IOException {

        Path temp = resolveTempJar(s, artifact, version, channel);

        Path target = resolveBaseJar(s, artifact, version, channel);

        Files.write(temp, data);

        Files.move(
                temp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

}
