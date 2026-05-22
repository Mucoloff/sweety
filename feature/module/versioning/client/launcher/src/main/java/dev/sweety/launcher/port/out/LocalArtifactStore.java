package dev.sweety.launcher.port.out;

import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Driven port: resolve the local filesystem path for a given artifact.
 */
public interface LocalArtifactStore {

    /**
     * Return the local path for {@code artifact}, or empty if not registered.
     */
    Optional<Path> pathFor(Artifact artifact);

    /**
     * Register (or update) the local path for {@code artifact}.
     */
    void register(Artifact artifact, Path path);
}
