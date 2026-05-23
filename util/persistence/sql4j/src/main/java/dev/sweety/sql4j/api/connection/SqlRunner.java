package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.api.interceptor.QueryInterceptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class responsible for executing {@link Query} objects against a JDBC {@link Connection}.
 *
 * <p>The static logger and slow-query threshold are held in atomic references so that
 * changes via {@link #setLogger(SqlLogger)} and {@link #setSlowQueryThresholdMs(long)}
 * are immediately visible on all threads without the risk of compound-update races.
 */
public final class SqlRunner {

    private static final AtomicReference<SqlLogger> logger = new AtomicReference<>(SqlLogger.nop());
    private static final AtomicLong slowQueryThresholdMs = new AtomicLong(500); // Default 500ms

    private SqlRunner() {}

    /**
     * Sets the global SQL logger. The change is immediately visible on all threads.
     * Defaults to {@link SqlLogger#nop()} (no logging).
     */
    public static void setLogger(SqlLogger newLogger) {
        logger.set(newLogger);
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
        slowQueryThresholdMs.set(threshold);
    }

    /**
     * Returns the currently active global {@link SqlLogger}.
     *
     * @return the non-null logger (may be {@link SqlLogger#nop()} if logging is disabled)
     */
    public static SqlLogger getLogger() {
        return logger.get();
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
        return execute(con, query, Collections.emptyList());
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

        // Snapshot both atomics once so they stay consistent for the lifetime of this execution.
        final SqlLogger log = logger.get();
        final long thresholdMs = slowQueryThresholdMs.get();

        long start = System.nanoTime();
        try (PreparedStatement ps = query.returnGeneratedKeys()
                ? con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
                : con.prepareStatement(sql)) {

            log.log("[Thread-%d] Executing SQL: %s", Thread.currentThread().threadId(), sql);
            query.bind(ps);
            T result = query.execute(ps);

            long duration = System.nanoTime() - start;
            double durationMs = duration / 1_000_000.0;
            log.log("[Thread-%d] SQL Executed in %.2fms", Thread.currentThread().threadId(), durationMs);
            if (durationMs > thresholdMs) {
                log.log("[WARNING] SLOW QUERY DETECTED: %.2fms for SQL: %s", durationMs, sql);
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
