package dev.sweety.util.logger;

import java.time.Instant;

public class SimpleLogger {

    private final String name;

    public SimpleLogger(Class<?> owner) {
        this.name = owner.getSimpleName();
    }

    public void info(String message) {
        log("INFO", message, null);
    }

    public void warn(String message) {
        log("WARN", message, null);
    }

    public void error(String message) {
        log("ERROR", message, null);
    }

    public void error(String message, Throwable throwable) {
        log("ERROR", message, throwable);
    }

    private void log(String level, String message, Throwable throwable) {
        String prefix = "[" + Instant.now() + "][" + level + "][" + this.name + "] ";
        System.err.println(prefix + message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }
}
