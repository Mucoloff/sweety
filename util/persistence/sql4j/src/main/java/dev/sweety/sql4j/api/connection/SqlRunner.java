package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.util.SqlLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Utility class responsible for executing {@link Query} objects against a JDBC {@link Connection}.
 *
 * <p>The static logger is {@code volatile} so that changes via {@link #setLogger(SqlLogger)}
 * are immediately visible across all threads, including executor worker threads.
 */
public final class SqlRunner {

    private static volatile SqlLogger logger = SqlLogger.nop();

    private SqlRunner() {}

    /**
     * Sets the global SQL logger. The change is immediately visible on all threads.
     * Defaults to {@link SqlLogger#nop()} (no logging).
     */
    public static void setLogger(SqlLogger newLogger) {
        logger = newLogger;
    }

    public static SqlLogger getLogger() {
        return logger;
    }

    public static <T> T execute(Connection con, Query<T> query) throws SQLException {
        final String sql = query.sql();
        try (PreparedStatement ps = con.prepareStatement(
                sql,
                query.returnGeneratedKeys()
                        ? PreparedStatement.RETURN_GENERATED_KEYS
                        : PreparedStatement.NO_GENERATED_KEYS)) {

            logger.log("[Thread-%d] Executing SQL: %s", Thread.currentThread().threadId(), sql);
            query.bind(ps);
            return query.execute(ps);
        }
    }
}
