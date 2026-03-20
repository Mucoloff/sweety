package dev.sweety.versioning.exception;

import dev.sweety.core.exception.Except;

public class InvalidTokenException extends Except {
    public InvalidTokenException(String message) {
        super(message);
    }
}
