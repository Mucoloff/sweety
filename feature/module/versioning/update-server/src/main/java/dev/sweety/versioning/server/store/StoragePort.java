package dev.sweety.versioning.server.store;

import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Path;

public interface StoragePort {

    Path root();

    Path resolveArtifactPath(Artifact artifact);

    Path resolveMetadataPath(Artifact artifact);

    Path settings();

    static Path temp(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }
}
