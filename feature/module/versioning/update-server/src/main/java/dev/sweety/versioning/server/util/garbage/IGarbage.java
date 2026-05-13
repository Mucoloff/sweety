package dev.sweety.versioning.server.util.garbage;

import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;

public interface IGarbage<Key, Value> {
    Value add(Key key, Value value);

    Value get(Key key);

    Value consume(Key key);

    void lazyClear();

    void clearGarbage();

    void remove(Key key);
}
