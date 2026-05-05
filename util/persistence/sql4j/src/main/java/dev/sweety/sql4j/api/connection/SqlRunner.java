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

    public static void setSlowQueryThresholdMs(long threshold) {
        slowQueryThresholdMs = threshold;
    }

    public static SqlLogger getLogger() {
        return logger;
    }

    public static <T> T execute(Connection con, Query<T> query) throws SQLException {
        return execute(con, query, java.util.Collections.emptyList());
    }

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

    public static <T> java.util.stream.Stream<T> stream(Connection con, dev.sweety.sql4j.api.query.Query<List<T>> query, java.util.List<QueryInterceptor> interceptors) throws SQLException {
        final String sql = query.sql();

        for (QueryInterceptor interceptor : interceptors) {
            interceptor.preExecute(query, con);
        }

        long start = System.nanoTime();
        PreparedStatement ps = query.returnGeneratedKeys()
                ? con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
                : con.prepareStatement(sql);

        try {
            logger.log("[Thread-%d] Streaming SQL: %s", Thread.currentThread().threadId(), sql);
            query.bind(ps);
            java.sql.ResultSet rs = ps.executeQuery();

            long duration = System.nanoTime() - start;
            logger.log("[Thread-%d] Stream started in %.2fms", Thread.currentThread().threadId(), duration / 1_000_000.0);

            // Create a Spliterator to wrap the ResultSet
            java.util.Spliterator<T> spliterator = new java.util.Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, java.util.Spliterator.ORDERED) {
                @Override
                public boolean tryAdvance(java.util.function.Consumer<? super T> action) {
                    try {
                        List<T> next = query.execute(ps); // This is actually wrong because query.execute(ps) in SelectEntity reads EVERYTHING
                        // ... I need a different method for SelectEntity to read ONE row
                        return false; 
                    } catch (SQLException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }
            };
            // ... Wait, SelectEntity.execute(ps) reads the whole ResultSet.
            // I need SelectEntity to provide a row mapper.
            return null;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }

}
