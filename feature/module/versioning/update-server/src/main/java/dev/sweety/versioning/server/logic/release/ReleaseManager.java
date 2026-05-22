package dev.sweety.versioning.server.logic.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReleaseManager implements IReleaseService {
    private static final SimpleLogger LOGGER = new SimpleLogger(ReleaseManager.class);

    private final Map<Artifact, ReleaseState> states = new ConcurrentHashMap<>();
    private final Storage storage;

    public ReleaseManager(Storage storage) throws IOException {
        this.storage = storage;
        // Pre-register core artifacts
        getOrRegister(Artifact.APP);
        getOrRegister(Artifact.LAUNCHER);
    }

    private ReleaseState getOrRegister(Artifact artifact) {
        return states.computeIfAbsent(artifact, a -> {
            try {
                ReleaseState state = new ReleaseState(a, storage);
                loadOrDefault(state);
                return state;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public ReleaseInfo latest(Artifact artifact, Channel channel) {
        ReleaseState s = getOrRegister(artifact);
        synchronized (s.lock) {
            return s.latest(channel);
        }
    }

    @Override
    @NotNull
    public Collection<ReleaseInfo> history(Artifact artifact, Channel channel) {
        ReleaseState s = getOrRegister(artifact);
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

        JsonObject root = Utils.gson().fromJson(Files.readString(s.metadata()), JsonObject.class);

        for (Channel channel : Channel.values()) {
            JsonObject channelEntry = root.getAsJsonObject(channel.prettyName());
            if (channelEntry == null) continue;

            JsonObject latest = channelEntry.getAsJsonObject("latest");
            JsonArray hist = channelEntry.getAsJsonArray("history");
            if (hist != null) {
                hist.asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .map(this::deserialize)
                        .filter(info -> {
                            if (info.channel() == channel) return true;
                            LOGGER.warn("Invalid channel for release " + info + ", expected " + channel);
                            return false;
                        })
                        .forEach(s.history(channel)::addLast);
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
            s.history(channel).stream()
                    .map(this::serialize)
                    .forEach(hist::add);
            
            channelEntry.add("history", hist);
            root.add(channel.prettyName(), channelEntry);
        }

        Path tmpFile = Storage.temp(s.metadata());
        Files.writeString(tmpFile, Utils.gson().toJson(root));

        Files.move(tmpFile, s.metadata(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
                Float.parseFloat(obj.get("rollout").getAsString()), 
                Instant.parse(obj.get("updatedAt").getAsString())
        );
    }

    private Path resolveFile(Path path, Artifact artifact, Channel channel, Version version) throws IOException {
        final Path dir = version.resolve(path.resolve(channel.prettyName()));
        Files.createDirectories(dir);
        return dir.resolve(artifact.name() + "-" + version + ".jar");
    }

    private Path resolveTempJar(ReleaseState s, @NotNull Artifact artifact, Channel channel, Version version) throws IOException {
        return Storage.temp(resolveBaseJar(s, artifact, channel, version));
    }

    private Path resolveBaseJar(ReleaseState s, @NotNull Artifact artifact, Channel channel, Version version) throws IOException {
        return resolveFile(s.root(), artifact, channel, version);
    }

    @Override
    @NotNull
    public Path resolveBaseJar(@NotNull Artifact artifact, Channel channel, Version version) throws IOException {
        final ReleaseState s = getOrRegister(artifact);
        synchronized (s.lock) {
            return resolveBaseJar(s, artifact, channel, version);
        }
    }

    @Override
    public ReleaseInfo rollback(Artifact artifact, Channel channel) throws IOException {
        ReleaseState s = getOrRegister(artifact);
        synchronized (s.lock) {
            ReleaseInfo prev = s.history(channel).pollFirst();
            if (prev == null) return null;
            s.latest(channel, prev);
            persist(s);
            return prev;
        }
    }

    @Override
    public ReleaseInfo updateRollout(Artifact artifact, Channel channel, float rollout) throws IOException {
        ReleaseState s = getOrRegister(artifact);
        synchronized (s.lock) {
            ReleaseInfo current = s.latest(channel);
            ReleaseInfo next = current.withRollout(rollout);
            return applyNextRelease(s, channel, current, next);
        }
    }

    @Override
    public ReleaseInfo applyRelease(
            @NotNull Artifact artifact,
            @NotNull Channel channel,
            @Nullable Version version,
            @Nullable Float rollout,
            @Nullable byte[] jar
    ) throws IOException {
        if (version != null && jar == null) throw new IllegalArgumentException(artifact + ".jar missing");
        if (version == null && jar != null) throw new IllegalArgumentException("Version is required when jar is provided");

        ReleaseState s = getOrRegister(artifact);
        synchronized (s.lock) {
            if (version != null) writeJar(s, artifact, version, channel, jar);
            final ReleaseInfo current = s.latest(channel);
            final Version nextVer = version != null ? version : current.version();
            final ReleaseInfo next = ReleaseInfo.of(nextVer, channel, rollout);
            return applyNextRelease(s, channel, current, next);
        }
    }

    private ReleaseInfo applyNextRelease(ReleaseState s, Channel channel, ReleaseInfo current, ReleaseInfo next) throws IOException {
        if (next.version().equals(current.version())
                && next.channel().equals(current.channel())
                && Float.compare(next.rollout(), current.rollout()) == 0) {
            return null;
        }

        s.history(channel).addFirst(current);
        while (s.history(channel).size() > Settings.HISTORY_LIMIT)
            s.history(channel).removeLast();

        s.latest(channel, next);
        persist(s);
        return next;
    }

    private void writeJar(ReleaseState s, Artifact artifact, Version version, Channel channel, byte[] data) throws IOException {
        Path temp = resolveTempJar(s, artifact, channel, version);
        Path target = resolveBaseJar(s, artifact, channel, version);
        Files.write(temp, data);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    @NotNull
    public ReleaseInfo resolveLatest(@NotNull Artifact artifact, @NotNull Channel channel) {
        ReleaseInfo latest = null;
        for (Channel ch : Channel.values()) {
            if (channel.accepts(ch)) {
                ReleaseInfo candidate = latest(artifact, ch);
                if (latest == null || candidate.updatedAt().isAfter(latest.updatedAt())) {
                    latest = candidate;
                }
            }
        }
        return latest != null ? latest : latest(artifact, channel);
    }
}
