package dev.sweety.versioning.server.util;

import dev.sweety.time.Expirable;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import manifold.util.concurrent.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Garbage<Key, Value extends Expirable> {

    private final Map<Key, Value> data = new ConcurrentHashMap<>();
    private final Set<Key> garbage = new ConcurrentHashSet<>();

    private final int maxGarbage;

    public Garbage(int maxGarbage) {
        this.maxGarbage = maxGarbage;
    }

    /**
     * Genera un token e lo aggiunge al map + garbage
     */
    public synchronized Value add(final Key key, final Value value) {
        data.put(key, value);
        garbage.add(key);
        if (garbage.size() > maxGarbage) clearGarbage();
        return value;
    }

    /**
     * Cerca e rimuove un token valido
     */
    public synchronized @NotNull Value consume(Key key) throws TokenExpiredException, InvalidTokenException {
        final Value value = data.remove(key);
        garbage.remove(key);

        if (value == null) throw new InvalidTokenException("value not found!");
        if (value.expired()) throw new TokenExpiredException("value expired! " + value.expiryTime());
        return value;
    }

    /**
     * Rimuove i token scaduti o già rimossi dal map
     */
    public synchronized void clearGarbage() {

        garbage.removeIf(key -> {
            final Value value = data.get(key);

            // se il value non esiste più → rimuovi
            if (value == null) return true;

            // se il value è scaduto → rimuovi da map e da garbage
            if (value.expired()) {
                data.remove(key);
                return true;
            }

            return false;
        });
    }

    public void remove(Key key){
        data.remove(key);
        garbage.remove(key);
    }
}
