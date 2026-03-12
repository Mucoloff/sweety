package dev.sweety.versioning.server.cache;

import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;

import java.nio.file.Path;
import java.util.UUID;

public record CacheKey(Artifact artifact, Version version, UUID clientId) {

    public Path toPath(Path root, String extension) {
        final Path artifactDir = root.resolve(artifact().name().toLowerCase());
        final Path versionDir = version().resolve(artifactDir);
        return versionDir.resolve(clientId() + "."+extension);
    }

    public Path toPath(Path root) {
        return toPath(root, "jar");
    }

}