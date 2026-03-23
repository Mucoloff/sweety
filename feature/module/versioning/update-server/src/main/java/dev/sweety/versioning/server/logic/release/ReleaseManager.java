package dev.sweety.versioning.server.logic.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.logic.storage.Storage;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Deque;
import java.util.EnumMap;

public class ReleaseManager {

    private final EnumMap<Artifact, ReleaseState> states = new EnumMap<>(Artifact.class);

    public ReleaseManager(Storage storage) throws IOException {
        for (Artifact artifact : Artifact.values()) {
            ReleaseState state = new ReleaseState(artifact, storage);
            loadOrDefault(state);
            states.put(artifact, state);
        }
    }

    public ReleaseInfo latest(Artifact artifact, Channel channel) {
        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {
            return s.latest(channel);
        }
    }

    public Deque<ReleaseInfo> history(Artifact artifact, Channel channel) {
        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {
            return s.history(channel);
        }
    }

    private void loadOrDefault(ReleaseState s) throws IOException {
        if (!Files.exists(s.metadata())) {
            for (Channel channel : Channel.values()) s.latest(channel, ReleaseInfo.DEFAULT(channel));
            persist(s);
            return;
        }

        JsonObject root = Utils.GSON.fromJson(
                Files.readString(s.metadata()),
                JsonObject.class
        );

        for (Channel channel : Channel.values()) {
            JsonObject channelEntry = root.getAsJsonObject(channel.prettyName());
            if (channelEntry == null) continue;

            JsonObject latest = channelEntry.getAsJsonObject("latest");
            JsonArray hist = channelEntry.getAsJsonArray("history");
            if (hist != null) {
                for (JsonElement el : hist) {
                    final ReleaseInfo info = deserialize(el.getAsJsonObject());
                    if (info.channel() != channel) {
                        //todo
                        System.out.println("invalid channel for release " + info + "should be " + channel);
                        continue;
                    }
                    s.history(channel).addLast(info);
                }

            }

            s.latest(channel, deserialize(latest));
        }
    }

    private void persist(ReleaseState s) throws IOException {
        JsonObject root = new JsonObject();

        for (Channel channel : Channel.values()) {
            JsonObject channelEntry = new JsonObject();

            channelEntry.add("latest", serialize(s.latest(channel)));
            JsonArray hist = new JsonArray();

            for (ReleaseInfo info : s.history(channel)) {
                hist.add(serialize(info));
            }

            channelEntry.add("history", hist);

            root.add(channel.prettyName(), channelEntry);
        }


        Path tmpFile = Storage.temp(s.metadata());
        Files.writeString(tmpFile, Utils.GSON.toJson(root));

        Files.move(
                tmpFile,
                s.metadata(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    private JsonObject serialize(ReleaseInfo state) {
        JsonObject obj = new JsonObject();

        obj.addProperty("version", state.version().toString());
        obj.addProperty("channel", state.channel().prettyName());
        obj.addProperty("updatedAt", state.updatedAt().toString());
        obj.addProperty("rollout", state.rollout());

        return obj;
    }

    private ReleaseInfo deserialize(JsonObject obj) {
        return new ReleaseInfo(
                Version.parse(obj.get("version").getAsString()),
                Channel.valueOf(obj.get("channel").getAsString().toUpperCase()),
                Float.parseFloat(obj.get("rollout").getAsString()), Instant.parse(obj.get("updatedAt").getAsString())
        );
    }

    private Path resolveFile(Path path, Artifact artifact, Channel channel, Version version) throws IOException {
        final String name = artifact.prettyName();
        final Path dir = version.resolve(path.resolve(channel.prettyName()));
        Files.createDirectories(dir);
        return dir.resolve(name + "-" + version + ".jar");
    }

    private Path resolveTempJar(ReleaseState s, @NotNull Artifact artifact, Channel channel, Version version) throws IOException {
        Path base = resolveBaseJar(s, artifact, channel, version);
        return Storage.temp(base);
    }

    private Path resolveBaseJar(ReleaseState s, @NotNull Artifact artifact, Channel channel, Version version) throws IOException {
        return resolveFile(s.root(), artifact, channel, version);
    }

    public Path resolveBaseJar(@NotNull Artifact artifact, Channel channel, Version version) throws IOException {
        final ReleaseState s = states.get(artifact);
        synchronized (s.lock) {
            return resolveBaseJar(s, artifact, channel, version);
        }
    }

    public ReleaseInfo rollback(Artifact artifact, Channel channel) throws IOException {
        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {

            ReleaseInfo prev = s.history(channel).pollFirst();

            if (prev == null)
                return null;

            s.latest(channel, prev);

            persist(s);

            return prev;
        }
    }

    public ReleaseInfo updateRollout(Artifact artifact, Channel channel, float rollout) throws IOException {
        ReleaseState s = states.get(artifact);

        synchronized (s.lock) {
            ReleaseInfo current = s.latest(channel);

            ReleaseInfo next = current.withRollout(rollout);

            //todo this should be the same logic as applyRelease
            s.history(channel).addFirst(current);

            while (s.history(channel).size() > Settings.HISTORY_LIMIT)
                s.history(channel).removeLast();

            s.latest(channel, next);

            persist(s);

            return next;
        }
    }

    public ReleaseInfo applyRelease(
            Artifact artifact,
            String channel,
            String version,
            @Nullable Float rollout,
            byte[] jar
    ) throws IOException {
        //todo move validation
        Channel ch;
        try {
            ch = Channel.valueOf(channel.toUpperCase());
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

            ReleaseInfo current = s.latest(ch);

            Version nextVer = ver != null ? ver : current.version();

            ReleaseInfo next = ReleaseInfo.of(nextVer, ch, rollout);

            if (next.version().equals(current.version())
                    && next.channel().equals(current.channel())
                    && Float.compare(next.rollout(), current.rollout()) == 0) {
                return null;
            }

            s.history(ch).addFirst(current);

            while (s.history(ch).size() > Settings.HISTORY_LIMIT)
                s.history(ch).removeLast();

            s.latest(ch, next);

            persist(s);

            return next;
        }
    }

    private void writeJar(
            ReleaseState s,
            Artifact artifact,
            Version version,
            Channel channel,
            byte[] data
    ) throws IOException {
        Path temp = resolveTempJar(s, artifact, channel, version);

        Path target = resolveBaseJar(s, artifact, channel, version);

        Files.write(temp, data);

        Files.move(
                temp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    public ReleaseInfo resolveLatest(Artifact artifact, Channel channel) {
        ReleaseInfo latest = null;
        for (Channel ch : Channel.values()) {
            if (channel.accepts(ch)) {
                ReleaseInfo candidate = latest(artifact, ch);
                if (latest == null || candidate.updatedAt().isAfter(latest.updatedAt())) {
                    latest = candidate;
                }
            }
        }

        if (latest == null) latest = latest(artifact, channel);
        return latest;
    }
}
