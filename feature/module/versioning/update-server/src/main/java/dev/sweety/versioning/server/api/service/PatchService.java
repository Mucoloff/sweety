package dev.sweety.versioning.server.api.service;

import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Patch resolution and differential computation contract.
 */
public interface PatchService {

    Optional<Path> cached(@NotNull Artifact artifact,
                          @NotNull Channel channel,
                          @NotNull Version latest,
                          @NotNull Version current);

    Optional<Path> generatePatch(@NotNull Artifact artifact,
                                 @NotNull Channel channel,
                                 @NotNull Version latest,
                                 @NotNull Version current);
}
