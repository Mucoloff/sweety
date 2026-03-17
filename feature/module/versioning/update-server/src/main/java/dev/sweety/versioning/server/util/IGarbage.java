package dev.sweety.versioning.server.util;

import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;

public interface IGarbage<Key, Value> {
    Value add(Key key, Value value);

    Value consume(Key key) throws TokenExpiredException, InvalidTokenException;

    void lazyClear();

    void clearGarbage();

    void remove(Key key);
}
