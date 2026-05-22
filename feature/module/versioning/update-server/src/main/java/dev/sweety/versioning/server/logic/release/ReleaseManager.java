package dev.sweety.versioning.server.logic.release;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.server.port.in.PublishReleaseUseCase;
import dev.sweety.versioning.server.port.in.RollbackReleaseUseCase;
import dev.sweety.versioning.server.port.out.ReleaseRepository;
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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReleaseManager implements IReleaseService, PublishReleaseUseCase, RollbackReleaseUseCase {
    private static final SimpleLogger LOGGER = new SimpleLogger(ReleaseManager.class);

    private final Map<Artifact, ReleaseState> states = new ConcurrentHashMap<>();
    private final Storage storage;
    private final ReleaseRepository repository;

    public ReleaseManager(Storage storage, ReleaseRepository repository) throws IOException {
        this.storage = storage;
        this.repository = repository;
        getOrRegister(Artifact.APP);
        getOrRegister(Artifact.LAUNCHER);
    }

    private ReleaseState getOrRegister(Artifact artifact) {
        return states.computeIfAbsent(artifact, a -> {
            try {
                ReleaseState state = new ReleaseState(a, storage);
                repository.load(a, state);
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
            repository.save(artifact, s);
            return prev;
        }
    }

    @Override
    public ReleaseInfo updateRollout(Artifact artifact, Channel channel, float rollout) throws IOException {
        ReleaseState s = getOrRegister(artifact);
        synchronized (s.lock) {
            ReleaseInfo current = s.latest(channel);
            ReleaseInfo next = current.withRollout(rollout);
            return applyNextRelease(artifact, s, channel, current, next);
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
            return applyNextRelease(artifact, s, channel, current, next);
        }
    }

    private ReleaseInfo applyNextRelease(Artifact artifact, ReleaseState s, Channel channel, ReleaseInfo current, ReleaseInfo next) throws IOException {
        if (next.version().equals(current.version())
                && next.channel().equals(current.channel())
                && Float.compare(next.rollout(), current.rollout()) == 0) {
            return null;
        }

        s.history(channel).addFirst(current);
        while (s.history(channel).size() > Settings.HISTORY_LIMIT)
            s.history(channel).removeLast();

        s.latest(channel, next);
        repository.save(artifact, s);
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
