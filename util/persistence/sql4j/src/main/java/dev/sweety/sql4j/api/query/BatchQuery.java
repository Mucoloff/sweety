package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * A query that inserts or updates multiple rows in a single JDBC batch.
 *
 * <p>The result is an {@code int[]} where each element is the JDBC update-count for the
 * corresponding row ({@link java.sql.Statement#SUCCESS_NO_INFO} if the driver does not
 * return per-row counts).
 *
 * <p>When a {@code batchChunkSize} is configured (via
 * {@link dev.sweety.sql4j.SQL4J.OpenStep#batchChunkSize(int)}), the collection is split into
 * sub-lists and each sub-list is executed in a separate {@code executeBatch()} call on the
 * same JDBC connection. The returned array is the concatenation of all sub-results.
 *
 * @param <T> the entity type being batch-operated on
 */
public non-sealed interface BatchQuery<T> extends Query<int[]> {

    /**
     * Executes the batch asynchronously on the given {@link SqlConnection}.
     * When chunking is enabled, all chunks run on a single borrowed connection.
     *
     * @param connection the SQL4J connection
     * @return a {@link java.util.concurrent.CompletableFuture} completing with per-row update counts
     */
    @Override
    default CompletableFuture<int[]> execute(final SqlConnection connection) {
        return connection.executeAsync(this);
    }
}
