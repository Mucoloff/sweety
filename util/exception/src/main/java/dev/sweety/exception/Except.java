package dev.sweety.exception;

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

    private boolean noStackTrace = false;

    public void addStackTrace(StackTraceElement[] trace) {
        if (noStackTrace) return;
        StackTraceElement[] preStacktrace = this.getStackTrace();
        StackTraceElement[] stackTrace = new StackTraceElement[preStacktrace.length + trace.length];
        System.arraycopy(preStacktrace, 0, stackTrace, 0, preStacktrace.length);
        System.arraycopy(trace, 0, stackTrace, preStacktrace.length, trace.length);
        this.setStackTrace(stackTrace);
    }

    public void noStackTrace() {
        this.noStackTrace = true;
        this.setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        if (noStackTrace) return super.fillInStackTrace();
        return this;
    }
}
