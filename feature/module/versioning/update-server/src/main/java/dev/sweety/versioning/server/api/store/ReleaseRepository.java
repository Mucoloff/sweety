package dev.sweety.versioning.server.api.store;

import dev.sweety.versioning.server.data.ReleaseState;
import dev.sweety.versioning.version.artifact.Artifact;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Storage SPI contract for persisting and loading release states.
 */
public interface ReleaseRepository {
    void load(@NotNull Artifact artifact, @NotNull ReleaseState state) throws IOException;
    void save(@NotNull Artifact artifact, @NotNull ReleaseState state) throws IOException;
}
