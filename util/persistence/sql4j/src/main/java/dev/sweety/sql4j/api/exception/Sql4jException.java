package dev.sweety.sql4j.api.exception;

/**
 * Root of the SQL4J exception hierarchy. All SQL4J runtime exceptions extend this class,
 * allowing callers to catch the entire family with a single {@code catch (Sql4jException e)}.
 */
public class Sql4jException extends RuntimeException {

    public Sql4jException(String message) {
        super(message);
    }

    public Sql4jException(String message, Throwable cause) {
        super(message, cause);
    }

    public Sql4jException(Throwable cause) {
        super(cause);
    }
}
