package dev.sweety.versioning.server.util;

import dev.sweety.time.Expirable;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;

public class Garbage<Key, Value> implements IGarbage<Key, Value> {

    private final ExpirableGarbage<Key, Container<Value>> internal;
    private final long delay;

    public Garbage(int maxGarbage, long delay) {
        this.internal = new ExpirableGarbage<>(maxGarbage);
        this.delay = delay;
    }

    public Garbage(int maxGarbage, float loadFactor, long delay) {
        this.internal = new ExpirableGarbage<>(maxGarbage, loadFactor);
        this.delay = delay;
    }

    @Override
    public Value add(Key key, Value value) {
        return internal.add(key, new Container<>(value, System.currentTimeMillis() + this.delay)).value();
    }

    @Override
    public Value consume(Key key) throws TokenExpiredException, InvalidTokenException {
        return internal.consume(key).value();
    }

    @Override
    public void lazyClear() {
        internal.lazyClear();
    }

    @Override
    public void clearGarbage() {
        internal.clearGarbage();
    }

    @Override
    public void remove(Key key) {
        internal.remove(key);
    }

    private record Container<Value>(Value value, long expireAt) implements Expirable {

    }
}
