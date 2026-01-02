package dev.sweety.core.exception;

public class AuthFailureException extends Except {

    public AuthFailureException(String message, Throwable e) {
        super(message, e);
    }

    public AuthFailureException(String message) {
        super(message);
    }

    public AuthFailureException(Throwable cause) {
        super(cause);
    }
}
