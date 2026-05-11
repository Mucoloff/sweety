package dev.sweety.sql4j.api.exception;

/**
 * Thrown when SQL4J fails to execute a SQL statement — e.g. a constraint violation, syntax
 * error, or timeout. The original {@link java.sql.SQLException} SQLState and vendor error code
 * are preserved for diagnostic purposes.
 */
public class Sql4jQueryException extends Sql4jException {

    private final String sqlState;
    private final int errorCode;

    public Sql4jQueryException(String message, java.sql.SQLException cause) {
        super(message, cause);
        this.sqlState = cause != null ? cause.getSQLState() : null;
        this.errorCode = cause != null ? cause.getErrorCode() : 0;
    }

    public Sql4jQueryException(String message, Throwable cause) {
        super(message, cause);
        if (cause instanceof java.sql.SQLException sqlEx) {
            this.sqlState = sqlEx.getSQLState();
            this.errorCode = sqlEx.getErrorCode();
        } else {
            this.sqlState = null;
            this.errorCode = 0;
        }
    }

    public Sql4jQueryException(String message) {
        super(message);
        this.sqlState = null;
        this.errorCode = 0;
    }

    /**
     * @return the JDBC SQLState string, or {@code null} if unavailable.
     */
    public String getSqlState() {
        return sqlState;
    }

    /**
     * @return the vendor-specific error code, or {@code 0} if unavailable.
     */
    public int getErrorCode() {
        return errorCode;
    }
}
