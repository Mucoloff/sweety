package dev.sweety.versioning.server.rollback;

import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.channel.Channel;

@FunctionalInterface
public interface RollbackConsumer {

    void rollback(Artifact artifact, Channel channel, ReleaseInfo rolled, ReleaseInfo prev);
}
