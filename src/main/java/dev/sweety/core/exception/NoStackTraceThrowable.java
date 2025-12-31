package dev.sweety.core.exception;

public class NoStackTraceThrowable extends Except {

    public NoStackTraceThrowable(Throwable cause) {
        super(cause);
        this.setStackTrace(new StackTraceElement[0]);
    }

    public NoStackTraceThrowable(String message, Throwable e) {
        super(message, e);
        this.setStackTrace(new StackTraceElement[0]);
    }

    public NoStackTraceThrowable(String message) {
        super(message);
        this.setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
