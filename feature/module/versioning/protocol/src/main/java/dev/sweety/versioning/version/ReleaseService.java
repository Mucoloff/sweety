package dev.sweety.versioning.version;

import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public interface ReleaseService {

    @Nullable
    ReleaseInfo latest(@NotNull Artifact artifact, @NotNull Channel channel);

    @NotNull
    Collection<ReleaseInfo> history(@NotNull Artifact artifact, @NotNull Channel channel);

    @NotNull
    Path resolveBaseJar(@NotNull Artifact artifact, @NotNull Channel channel, @NotNull Version version) throws IOException;

    @Nullable
    ReleaseInfo rollback(@NotNull Artifact artifact, @NotNull Channel channel) throws IOException;

    @Nullable
    ReleaseInfo updateRollout(@NotNull Artifact artifact, @NotNull Channel channel, float rollout) throws IOException;

    @Nullable
    ReleaseInfo applyRelease(
            @NotNull Artifact artifact,
            @NotNull Channel channel,
            @Nullable Version version,
            @Nullable Float rollout,
            @Nullable byte[] jar
    ) throws IOException;

    @NotNull
    ReleaseInfo resolveLatest(@NotNull Artifact artifact, @NotNull Channel channel);
}
