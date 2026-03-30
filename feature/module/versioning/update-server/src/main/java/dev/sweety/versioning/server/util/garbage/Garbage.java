package dev.sweety.versioning.server.util.garbage;

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

    @Override
    public Value add(Key key, Value value) {
        return unwrap(internal.add(key, wrap(value)));
    }

    @Override
    public Value get(Key key) throws TokenExpiredException, InvalidTokenException {
        return unwrap(internal.get(key));
    }

    @Override
    public Value consume(Key key) throws TokenExpiredException, InvalidTokenException {
        return unwrap(internal.consume(key));
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

    private Container<Value> wrap(Value value) {
        return new Container<>(value, System.currentTimeMillis() + this.delay);
    }

    private static <Value> Value unwrap(Container<Value> container) {
        return container == null ? null : container.value();
    }

    private record Container<Value>(Value value, long expireAt) implements Expirable {

    }
}
