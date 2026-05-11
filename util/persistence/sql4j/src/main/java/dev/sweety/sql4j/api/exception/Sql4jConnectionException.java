package dev.sweety.sql4j.api.exception;

/**
 * Thrown when SQL4J cannot acquire or configure a database connection — e.g. the connection
 * pool fails to start, a required configuration field is missing, or the JDBC URL is invalid.
 */
public class Sql4jConnectionException extends Sql4jException {

    public Sql4jConnectionException(String message) {
        super(message);
    }

    public Sql4jConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public Sql4jConnectionException(Throwable cause) {
        super(cause);
    }
}
