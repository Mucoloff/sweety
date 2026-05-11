package dev.sweety.sql4j.api.exception;

/**
 * Thrown when the SQL4J entity cache encounters an inconsistency — e.g. a failed eviction
 * attempt or a cache configuration error.
 */
public class Sql4jCacheException extends Sql4jException {

    public Sql4jCacheException(String message) {
        super(message);
    }

    public Sql4jCacheException(String message, Throwable cause) {
        super(message, cause);
    }

    public Sql4jCacheException(Throwable cause) {
        super(cause);
    }
}
