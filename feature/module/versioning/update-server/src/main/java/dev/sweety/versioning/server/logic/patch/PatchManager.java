package dev.sweety.versioning.server.logic.patch;

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
import java.util.*;

public class PatchManager {

    private static final String BUILD_INFO = "dev/sweety/build/BuildInfo.class";

    private final Map<Artifact, Map<Channel, Map<Version, Map<Version, File>>>> patchCache = new EnumMap<>(Artifact.class);

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
            final Version to = history.pollFirst().version();
            try {
                generatePatch(newJar, artifact, channel, latest, to);
            } catch (IllegalArgumentException ignored) {

            }
            distance--;
        }
    }

    private void generatePatch(File newJar, Artifact artifact, Channel channel, Version latest, Version old) throws IOException {
        File oldJar = this.releaseManager.resolveBaseJar(artifact, channel, old).toFile();

        final String fromVer = old.toString();
        final String toVer = latest.toString();

        Path versionRoot = latest.resolve(this.artifacts.get(artifact).resolve(channel.prettyName()));
        File path = versionRoot.resolve("patch").toFile();
        path.mkdirs();
        File patch = generator.generate(oldJar, newJar, path, "v" + fromVer, fromVer, toVer, EXCLUDE_SIGNATURE);

        versionFileMap(artifact, channel, latest).put(old, patch);
    }

    private @NotNull Map<Version, File> versionFileMap(Artifact artifact, Channel channel, Version latest) {
        return this.patchCache
                .computeIfAbsent(artifact, _ignored -> new EnumMap<>(Channel.class))
                .computeIfAbsent(channel, _ignored -> new HashMap<>())
                .computeIfAbsent(latest, _ignored -> cache());
    }

    private static Map<Version, File> cache() {
        return new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Version, File> eldest) {
                return size() > Settings.MAX_PATCH_VER_DISTANCE;
            }
        };
    }


    public Optional<File> cached(Artifact artifact, Channel channel, Version latest, Version current) {
        File file = versionFileMap(artifact, channel, latest).get(current);
        return Optional.ofNullable(file);
    }
}
