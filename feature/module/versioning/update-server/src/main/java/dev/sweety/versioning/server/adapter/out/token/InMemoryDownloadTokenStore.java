package dev.sweety.versioning.server.adapter.out.token;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.domain.download.Token;
import dev.sweety.versioning.server.port.out.DownloadTokenStore;
import dev.sweety.time.store.ExpiryCache;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.exception.*;
import dev.sweety.versioning.version.Version;
import dev.sweety.data.ObjectUtils;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class InMemoryDownloadTokenStore extends ExpiryCache<UUID, Token> implements DownloadTokenStore {

    public InMemoryDownloadTokenStore() {
        super(Settings.MAX_CONCURRENT_DOWNLOADS);
    }

    @Override
    public String generate(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType) {
        final Token token = new Token(clientId, artifact, channel, version, from, downloadType, Settings.DOWNLOAD_EXPIRE_DELAY_MS);
        final UUID tokenId = token.token();
        add(tokenId, token);
        return tokenId.toString();
    }

    @Override
    public @Nullable Token consume(String tokenId) {
        final UUID id;
        try {
            id = ObjectUtils.parseUuid(tokenId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return super.consume(id);
    }
}
