package dev.sweety.versioning.server.api.store;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.data.Token;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Ephemeral download token management contract.
 */
public interface DownloadTokenStore {
    String generate(@NotNull UUID clientId,
                    @NotNull Artifact artifact,
                    @NotNull Channel channel,
                    @NotNull Version version,
                    @Nullable Version from,
                    @NotNull DownloadType downloadType);

    @Nullable
    Token consume(@NotNull String tokenId);
}
