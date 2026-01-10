package dev.sweety.core.logger.backend;

import dev.sweety.core.logger.LogLevel;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Backend basato su java.util.logging (JUL), presente nel JDK.
 * Non richiede dipendenze esterne e mantiene l'indipendenza da SLF4J.
 */
public class JulBackend implements LoggerBackend {

    private final Logger logger;

    public JulBackend(String loggerName) {
        this.logger = Logger.getLogger(loggerName);
    }

    @Override
    public void log(LogLevel level, String loggerName, String profile, String formattedLine) {
        logger.log(map(level), formattedLine);
    }

    private static Level map(LogLevel level) {
        return switch (level) {
            case ERROR -> Level.SEVERE;
            case WARN -> Level.WARNING;
            case INFO -> Level.INFO;
            case DEBUG -> Level.FINE;
            case TRACE -> Level.FINER;
        };
    }
}

