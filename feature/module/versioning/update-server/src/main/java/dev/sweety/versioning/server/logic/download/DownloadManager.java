package dev.sweety.versioning.server.logic.download;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.util.garbage.ExpirableGarbage;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.exception.*;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.server.logic.token.Token;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DownloadManager extends ExpirableGarbage<UUID, Token> {
    /**
     * expire in millis
     */

    public DownloadManager() {
        super(Settings.MAX_CONCURRENT_DOWNLOADS);
    }

    /**
     * Genera un token e lo aggiunge al map + garbage
     */
    public synchronized String generate(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType) {

        final Token token = new Token(clientId, artifact, channel, version, from, downloadType, Settings.DOWNLOAD_EXPIRE_DELAY_MS);
        final UUID tokenId = token.token();

        add(tokenId, token);

        return tokenId.toString();
    }

    /**
     * Cerca e rimuove un token valido
     */
    public synchronized @NotNull Token search(String tokenId) throws TokenExpiredException, InvalidTokenException {
        final UUID id;
        try {
            id = Utils.parseUuid(tokenId);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("invalid token!");
        }

        return super.consume(id);
    }

}