package dev.sweety.versioning.server.service;

import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface PublishReleaseUseCase {
    @Nullable ReleaseInfo applyRelease(Artifact artifact, Channel channel, @Nullable Version version, @Nullable Float rollout, @Nullable byte[] jar) throws IOException;
    @Nullable ReleaseInfo updateRollout(Artifact artifact, Channel channel, float rollout) throws IOException;
}
