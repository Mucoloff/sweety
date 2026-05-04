package dev.sweety.sql4j.api.query.functions;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Executes a {@link PreparedStatement} and returns a result.
 * Note: intentionally does NOT extend {@link QueryBinder} — the two are orthogonal concerns.
 */
@FunctionalInterface
public interface QueryExecutor<T> {
    T execute(PreparedStatement ps) throws SQLException;

    /** A no-op executor that returns null. */
    QueryExecutor<?> EMPTY = _ -> null;
}
