package dev.sweety.core.exception;

public abstract class Except extends Exception {
    public Except(String message, Throwable cause) {
        super(message, cause);
    }

    public Except(String message) {
        super(message);
    }

    public Except(Throwable cause) {
        super(cause);
    }

    public RuntimeException runtime() {
        return new RuntimeException(this);
    }

    public NoStackTraceThrowable noStackTrace(){
        return new NoStackTraceThrowable(this);
    }
}
