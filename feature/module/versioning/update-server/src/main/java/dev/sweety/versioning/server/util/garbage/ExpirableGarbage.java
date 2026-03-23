package dev.sweety.versioning.server.util.garbage;

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
    private final int maxGarbage;

    public ExpirableGarbage(int maxGarbage, float loadFactor) {
        this.maxGarbage = maxGarbage;
        int capacity = (int) Math.ceil(maxGarbage / loadFactor);
        this.data = new ConcurrentHashMap<>(capacity);
    }

    public ExpirableGarbage(int maxGarbage) {
        this(maxGarbage, 0.75f);
    }

    @Override
    public Value add(Key key, Value value) {
        lazyClear();
        return this.data.put(key, value);
    }

    @Override
    public @NotNull Value get(Key key) throws TokenExpiredException, InvalidTokenException {
        final Value value = this.data.get(key);

        if (value == null) throw new InvalidTokenException("value not found!");

        if (value.expired()) {
            this.data.remove(key);
            throw new TokenExpiredException("value expired! " + value.expiryTime());
        }

        return value;
    }

    @Override
    public @NotNull Value consume(Key key) throws TokenExpiredException, InvalidTokenException {
        final Value value = this.data.remove(key);

        if (value == null) throw new InvalidTokenException("value not found!");
        if (value.expired()) throw new TokenExpiredException("value expired! " + value.expiryTime());

        return value;
    }

    @Override
    public void lazyClear() {
        if (this.data.size() > this.maxGarbage) clearGarbage();
    }

    @Override
    public void clearGarbage() {
        this.data.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    @Override
    public void remove(Key key) {
        this.data.remove(key);
    }
}