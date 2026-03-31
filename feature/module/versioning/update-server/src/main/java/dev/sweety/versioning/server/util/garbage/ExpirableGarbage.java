package dev.sweety.versioning.server.util.garbage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import dev.sweety.time.Expirable;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class ExpirableGarbage<Key, Value extends Expirable> implements IGarbage<Key, Value> {

    private final Cache<Key, Value> cache;

    public ExpirableGarbage(int maxGarbage) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxGarbage)
                .expireAfter(new Expiry<Key, Value>() {
                    @Override
                    public long expireAfterCreate(Key key, Value value, long currentTime) {
                        if (!value.hasExpiry()) return Long.MAX_VALUE;
                        return TimeUnit.MILLISECONDS.toNanos(Math.max(0, value.expiryTime()));
                    }

                    @Override
                    public long expireAfterUpdate(Key key, Value value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(Key key, Value value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public Value add(Key key, Value value) {
        cache.put(key, value);
        return null; // old value unreliable in concurrent cache
    }

    @Override
    public @NotNull Value get(Key key) throws TokenExpiredException, InvalidTokenException {
        Value value = cache.getIfPresent(key);
        if (value == null) throw new InvalidTokenException("value not found!");
        if (value.expired()) {
            cache.invalidate(key);
            throw new TokenExpiredException("value expired! " + Math.max(0, value.expiryTime()));
        }
        return value;
    }

    @Override
    public @NotNull Value consume(Key key) throws TokenExpiredException, InvalidTokenException {
        Value value = cache.asMap().remove(key);
        if (value == null) throw new InvalidTokenException("value not found!");
        if (value.expired()) throw new TokenExpiredException("value expired! " + Math.max(0, value.expiryTime()));
        return value;
    }

    @Override
    public void lazyClear() {
        // Rimosso: inutile con Caffeine
    }

    @Override
    public void clearGarbage() {
        cache.cleanUp(); // opzionale, forza pulizia interna
    }

    @Override
    public void remove(Key key) {
        cache.invalidate(key);
    }
}