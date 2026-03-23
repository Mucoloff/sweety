package dev.sweety.versioning.server.logic.actions;

import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ReleaseBroadcastConsumer {

    void broadcast(Artifact artifact, ReleaseInfo target, Channel channel, ReleaseBroadcastType type, @Nullable ReleaseInfo previous);
}
