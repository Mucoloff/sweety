package dev.sweety.versioning.server.port.out;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.domain.download.Token;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface DownloadTokenStore {
    String generate(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType);
    @Nullable Token consume(String tokenId);
}
