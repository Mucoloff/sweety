package dev.sweety.versioning.server.download;

import dev.sweety.versioning.server.util.ExpirableGarbage;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.exception.*;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.server.token.Token;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DownloadManager extends ExpirableGarbage<UUID, Token> {
    /**
     * expire in millis
     */
    private static final long EXPIRE_DELAY_MS = 30_000L;
    private static final int MAX_GARBAGE = 50;

    public DownloadManager() {
        super(MAX_GARBAGE);
    }

    /**
     * Genera un token e lo aggiunge al map + garbage
     */
    public synchronized String generate(UUID clientId, Artifact artifact, Version version, Channel channel) {

        final Token token = new Token(clientId, artifact, version, channel, EXPIRE_DELAY_MS);
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