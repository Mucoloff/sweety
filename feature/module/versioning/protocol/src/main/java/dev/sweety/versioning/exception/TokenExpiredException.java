package dev.sweety.versioning.exception;

import dev.sweety.core.exception.Except;

public class TokenExpiredException extends Except {
    public TokenExpiredException(String message) {
        super(message);
    }
}
