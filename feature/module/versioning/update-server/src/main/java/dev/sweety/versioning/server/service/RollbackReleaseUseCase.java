package dev.sweety.versioning.server.service;

import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@FunctionalInterface
public interface RollbackReleaseUseCase {
    @Nullable ReleaseInfo rollback(Artifact artifact, Channel channel) throws IOException;
}
