package dev.sweety.versioning.server.download;

import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.exception.*;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.server.token.Token;
import dev.sweety.versioning.util.Utils;
import manifold.util.concurrent.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadManager {

    private final Map<UUID, Token> tokenMap = new ConcurrentHashMap<>();
    private final Set<UUID> garbage = new ConcurrentHashSet<>();

    /** expire in millis */
    private static final long EXPIRE_DELAY_MS = 30_000L;
    private static final int MAX_GARBAGE = 50;

    /**
     * Genera un token e lo aggiunge al map + garbage
     */
    public synchronized String generate(UUID clientId, Artifact artifact, Version version) {

        final Token token = new Token(clientId, artifact, version, EXPIRE_DELAY_MS);
        final UUID tokenId = token.token();

        tokenMap.put(tokenId, token);
        garbage.add(tokenId);

        if (garbage.size() > MAX_GARBAGE) clearGarbage();

        return tokenId.toString();
    }

    /**
     * Cerca e rimuove un token valido
     */
    public synchronized Token search(String tokenId) throws TokenExpiredException, InvalidTokenException {
        final UUID id;
        try {
            id = Utils.parseUuid(tokenId);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("invalid token!");
        }

        final Token token = tokenMap.remove(id);
        garbage.remove(id);

        if (token == null) throw new InvalidTokenException("token not found!");
        if (token.expired()) throw new TokenExpiredException("token expired! " + token.expiryTime());
        return token;
    }

    /**
     * Rimuove i token scaduti o già rimossi dal map
     */
    public synchronized void clearGarbage() {

        garbage.removeIf(id -> {
            Token token = tokenMap.get(id);

            // se il token non esiste più → rimuovi
            if (token == null) return true;

            // se il token è scaduto → rimuovi da map e da garbage
            if (token.expired()) {
                tokenMap.remove(id);
                return true;
            }

            return false;
        });
    }

}