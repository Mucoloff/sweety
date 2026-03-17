package dev.sweety.versioning.server.util;

import dev.sweety.time.Expirable;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import manifold.util.concurrent.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ExpirableGarbage<Key, Value extends Expirable> implements IGarbage<Key, Value> {

    private final Map<Key, Value> data;
    private final Set<Key> garbage;

    private final int maxGarbage;

    public ExpirableGarbage(int maxGarbage, float loadFactor) {
        this.maxGarbage = maxGarbage;
        int capacity = (int) Math.ceil(maxGarbage / loadFactor);
        this.data = new ConcurrentHashMap<>(capacity);
        this.garbage = new ConcurrentHashSet<>(capacity);
    }

    public ExpirableGarbage(int maxGarbage) {
        this(maxGarbage, 0.75f);
    }

    /**
     * Genera un token e lo aggiunge al map + garbage
     */
    @Override
    public synchronized Value add(final Key key, final Value value) {
        lazyClear();
        data.put(key, value);
        garbage.add(key);
        return value;
    }

    /**
     * Cerca e rimuove un token valido
     */
    @Override
    public synchronized @NotNull Value consume(Key key) throws TokenExpiredException, InvalidTokenException {
        final Value value = data.remove(key);
        garbage.remove(key);

        if (value == null) throw new InvalidTokenException("value not found!");
        if (value.expired()) throw new TokenExpiredException("value expired! " + value.expiryTime());
        return value;
    }

    @Override
    public void lazyClear() {
        if (garbage.size() > maxGarbage) clearGarbage();
    }

    /**
     * Rimuove i token scaduti o già rimossi dal map
     */
    @Override
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

    @Override
    public void remove(Key key) {
        data.remove(key);
        garbage.remove(key);
    }
}
