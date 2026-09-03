package dev.sweety.versioning.server.api.service;

import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import dev.sweety.versioning.version.Version;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Unified release orchestration service contract for update-server.
 */
public interface ReleaseService extends dev.sweety.versioning.version.ReleaseService {

    Optional<Path> jarPath(@NotNull Artifact artifact,
                           @NotNull Channel channel,
                           @NotNull Version version);
}
