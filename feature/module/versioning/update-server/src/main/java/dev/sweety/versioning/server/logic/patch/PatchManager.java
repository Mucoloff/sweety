package dev.sweety.versioning.server.logic.patch;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sweety.patch.bytecode.AsmClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.filter.DefaultPatchFilter;
import dev.sweety.patch.generator.PatchGenerator;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.logic.cache.CacheKey;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.logic.storage.Storage;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class PatchManager {

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

    private final PatchGenerator generator = new PatchGenerator(new Sha256Hash(), new AsmClassNormalizer(), PatchTypes.BIN);

    private final EnumMap<Artifact, Path> artifacts;
    private final ReleaseManager releaseManager;

    public PatchManager(Storage storage, ReleaseManager releaseManager) {
        this.artifacts = storage.artifacts();
        this.releaseManager = releaseManager;
    }

    public File generatePatch(CacheKey key, Version from) throws IOException {
        File cachedPath = key.toPath(artifacts.get(key.artifact())).toFile();
        File dir = key.toPath(artifacts.get(key.artifact()), "v" + from.toString()).toFile();
        dir.mkdirs();
        CacheKey oldKey = new CacheKey(key.artifact(), key.channel(), from, key.clientId());
        File oldPath = oldKey.toPath(artifacts.get(oldKey.artifact())).toFile();

        return generator.generate(oldPath, cachedPath, dir, key.clientId().toString(), from.toString(), key.version().toString(), ONLY_SIGNATURE);
    }

    public void generatePatch(Artifact artifact, Channel channel, Version latest) throws IOException {
        int distance = Settings.MAX_PATCH_VER_DISTANCE;

        final Deque<ReleaseInfo> history = new ArrayDeque<>(releaseManager.history(artifact, channel));
        File newJar = this.releaseManager.resolveBaseJar(artifact, channel, latest).toFile();

        while (distance > 0 && !history.isEmpty()) {
            Version old = history.pollFirst().version();

            try {
                generatePatch(newJar, artifact, channel, latest, old);
            } catch (IllegalArgumentException ignored) {
            }

            distance--;
        }
    }

    public Optional<File> cached(Artifact artifact, Channel channel, Version latest, Version current) {
        PatchBucket bucket = bucket(artifact, channel, latest);
        return Optional.ofNullable(bucket.get(current));
    }

    private void generatePatch(File newJar, Artifact artifact, Channel channel, Version latest, Version old) throws IOException {

        PatchBucket bucket = bucket(artifact, channel, latest);

        File existing = bucket.get(old);
        if (existing != null) {
            return;
        }

        File oldJar = this.releaseManager.resolveBaseJar(artifact, channel, old).toFile();

        final String fromVer = old.toString();
        final String toVer = latest.toString();

        Path versionRoot = latest.resolve(this.artifacts.get(artifact).resolve(channel.prettyName()));
        File path = versionRoot.resolve("patch").toFile();
        path.mkdirs();

        File patch = generator.generate(
                oldJar,
                newJar,
                path,
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
        private final Cache<Version, File> cache = Caffeine.newBuilder()
                .maximumSize(Settings.MAX_PATCH_VER_DISTANCE)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();

        public File get(Version from) {
            return cache.getIfPresent(from);
        }

        public void put(Version from, File file) {
            cache.put(from, file);
        }
    }
}
