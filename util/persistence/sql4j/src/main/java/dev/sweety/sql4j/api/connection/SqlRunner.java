package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.api.interceptor.QueryInterceptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Utility class responsible for executing {@link Query} objects against a JDBC {@link Connection}.
 *
 * <p>The static logger is {@code volatile} so that changes via {@link #setLogger(SqlLogger)}
 * are immediately visible across all threads, including executor worker threads.
 */
public final class SqlRunner {

    private static volatile SqlLogger logger = SqlLogger.nop();
    private static volatile long slowQueryThresholdMs = 500; // Default 500ms

    private SqlRunner() {}

    /**
     * Sets the global SQL logger. The change is immediately visible on all threads.
     * Defaults to {@link SqlLogger#nop()} (no logging).
     */
    public static void setLogger(SqlLogger newLogger) {
        logger = newLogger;
    }

    /**
     * Sets the slow-query warning threshold.
     * Any query that takes longer than {@code threshold} milliseconds emits a
     * {@code [WARNING] SLOW QUERY DETECTED} log message via the current {@link SqlLogger}.
     * Default is {@code 500 ms}.
     *
     * @param threshold threshold in milliseconds; {@code 0} disables slow-query warnings
     */
    public static void setSlowQueryThresholdMs(long threshold) {
        slowQueryThresholdMs = threshold;
    }

    /**
     * Returns the currently active global {@link SqlLogger}.
     *
     * @return the non-null logger (may be {@link SqlLogger#nop()} if logging is disabled)
     */
    public static SqlLogger getLogger() {
        return logger;
    }

    /**
     * Executes a {@link Query} synchronously on the given JDBC connection without interceptors.
     *
     * @param <T>   the result type
     * @param con   an open JDBC connection (not closed by this method)
     * @param query the query to execute
     * @return the typed result
     * @throws SQLException if SQL execution or parameter binding fails
     */
    public static <T> T execute(Connection con, Query<T> query) throws SQLException {
        return execute(con, query, java.util.Collections.emptyList());
    }

    /**
     * Executes a {@link Query} synchronously on the given JDBC connection,
     * notifying all registered {@link QueryInterceptor interceptors} before and after execution.
     *
     * <p>The JDBC {@link java.sql.PreparedStatement} is closed in a {@code try-with-resources}
     * block. The {@code con} connection is <strong>not</strong> closed by this method.
     *
     * @param <T>          the result type
     * @param con          an open JDBC connection
     * @param query        the query to execute
     * @param interceptors interceptors to notify (may be empty)
     * @return the typed result
     * @throws SQLException if SQL preparation, binding, or execution fails
     */
    public static <T> T execute(Connection con, Query<T> query, List<QueryInterceptor> interceptors) throws SQLException {
        final String sql = query.sql();

        for (QueryInterceptor interceptor : interceptors) {
            interceptor.preExecute(query, con);
        }

        long start = System.nanoTime();
        try (PreparedStatement ps = query.returnGeneratedKeys()
                ? con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
                : con.prepareStatement(sql)) {

            logger.log("[Thread-%d] Executing SQL: %s", Thread.currentThread().threadId(), sql);
            query.bind(ps);
            T result = query.execute(ps);

            long duration = System.nanoTime() - start;
            double durationMs = duration / 1_000_000.0;
            logger.log("[Thread-%d] SQL Executed in %.2fms", Thread.currentThread().threadId(), durationMs);
            if (durationMs > slowQueryThresholdMs) {
                logger.log("[WARNING] SLOW QUERY DETECTED: %.2fms for SQL: %s", durationMs, sql);
            }

            for (QueryInterceptor interceptor : interceptors) {
                interceptor.postExecute(query, result, duration);
            }

            return result;
        } catch (Throwable t) {
            long duration = System.nanoTime() - start;
            for (QueryInterceptor interceptor : interceptors) {
                interceptor.onError(query, t, duration);
            }
            throw t;
        }
    }

}
