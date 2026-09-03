package dev.sweety.versioning.server.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sweety.patch.bytecode.AsmClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.filter.DefaultPatchFilter;
import dev.sweety.patch.generator.PatchGenerator;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.data.CacheKey;
import dev.sweety.versioning.server.service.ResolvePatchPort;
import dev.sweety.versioning.server.store.StoragePort;
import dev.sweety.versioning.version.ReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class PatchManager implements ResolvePatchPort {

    private static final String BUILD_INFO = "dev/sweety/build/BuildInfo.class";

    private final Cache<LatestKey, PatchBucket> buckets = Caffeine.newBuilder()
            .maximumSize(1_000)
            .build();

    private static final PatchFilter info = BUILD_INFO::equals;
    public static final PatchFilter ONLY_JAVA = new DefaultPatchFilter();
    public static final PatchFilter EXCLUDE_SIGNATURE = ONLY_JAVA.or(info);
    public static final PatchFilter ONLY_SIGNATURE = path -> {
        if (!path.contains("META-INF")) return true;
        return ONLY_JAVA.or(info.not()).exclude(path);
    };

    private final PatchGenerator generator = new PatchGenerator(new Sha256Hash(), new AsmClassNormalizer(), PatchTypes.PATCH_JAR);

    private final StoragePort storage;
    private final ReleaseService releaseManager;

    public PatchManager(StoragePort storage, ReleaseService releaseManager) {
        this.storage = storage;
        this.releaseManager = releaseManager;
    }

    public Path generatePatch(CacheKey key, Version from) throws IOException {
        Path artifactPath = storage.resolveArtifactPath(key.artifact());
        Path cachedPath = key.toPath(artifactPath);
        Path dir = key.toPath(artifactPath, "v" + from.toString());
        Files.createDirectories(dir);
        CacheKey oldKey = new CacheKey(key.artifact(), key.channel(), from, key.clientId());
        Path oldPath = oldKey.toPath(artifactPath);

        return generator.generate(oldPath, cachedPath, dir, key.clientId().toString(), from.toString(), key.version().toString(), ONLY_SIGNATURE);
    }

    public void generatePatch(Artifact artifact, Channel channel, Version latest) throws IOException {
        int distance = Settings.MAX_PATCH_VER_DISTANCE;

        final Deque<ReleaseInfo> history = new ArrayDeque<>(releaseManager.history(artifact, channel));
        Path newJar = this.releaseManager.resolveBaseJar(artifact, channel, latest);

        while (distance > 0 && !history.isEmpty()) {
            Version old = history.pollFirst().version();

            try {
                generatePatch(newJar, artifact, channel, latest, old);
            } catch (IllegalArgumentException ignored) {
            }

            distance--;
        }
    }

    public Optional<Path> cached(Artifact artifact, Channel channel, Version latest, Version current) {
        PatchBucket bucket = bucket(artifact, channel, latest);
        return Optional.ofNullable(bucket.get(current));
    }

    private void generatePatch(Path newJar, Artifact artifact, Channel channel, Version latest, Version old) throws IOException {

        PatchBucket bucket = bucket(artifact, channel, latest);

        Path existing = bucket.get(old);
        if (existing != null) {
            return;
        }

        Path oldJar = this.releaseManager.resolveBaseJar(artifact, channel, old);

        final String fromVer = old.toString();
        final String toVer = latest.toString();

        Path versionRoot = latest.resolve(storage.resolveArtifactPath(artifact).resolve(channel.prettyName()));
        Path patchDir = versionRoot.resolve("patch");
        Files.createDirectories(patchDir);

        Path patch = generator.generate(
                oldJar,
                newJar,
                patchDir,
                "v" + fromVer,
                fromVer,
                toVer,
                EXCLUDE_SIGNATURE
        );

        bucket.put(old, patch);
    }

    private PatchBucket bucket(Artifact artifact, Channel channel, Version latest) {
        LatestKey key = new LatestKey(artifact, channel, latest);

        return buckets.get(key, _k -> new PatchBucket());
    }

    public record LatestKey(
            Artifact artifact,
            Channel channel,
            Version latest
    ) {
    }

    public static class PatchBucket {
        private final Cache<Version, Path> cache = Caffeine.newBuilder()
                .maximumSize(Settings.MAX_PATCH_VER_DISTANCE)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();

        public Path get(Version from) {
            return cache.getIfPresent(from);
        }

        public void put(Version from, Path path) {
            cache.put(from, path);
        }
    }

    @Override
    public Optional<Path> generatePatch(Artifact artifact, Channel channel, Version latest, Version current) {
        CacheKey key = new CacheKey(artifact, channel, latest, null);
        try {
            return Optional.ofNullable(generatePatch(key, current));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
