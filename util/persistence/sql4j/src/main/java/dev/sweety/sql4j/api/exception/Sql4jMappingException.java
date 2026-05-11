package dev.sweety.sql4j.api.exception;

/**
 * Thrown when SQL4J cannot map a database result to a Java entity, or when an entity's
 * annotations are malformed — e.g. missing primary key, inaccessible field, or failed
 * reflective instantiation.
 */
public class Sql4jMappingException extends Sql4jException {

    public Sql4jMappingException(String message) {
        super(message);
    }

    public Sql4jMappingException(String message, Throwable cause) {
        super(message, cause);
    }

    public Sql4jMappingException(Throwable cause) {
        super(cause);
    }
}
