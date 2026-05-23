package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public interface QueryChain<T> {

    Logger LOG = Logger.getLogger(QueryChain.class.getName());

    /**
     * Executes all steps using the given raw JDBC {@link Connection}.
     * <b>No transaction wrapping is applied</b> — the caller is responsible for
     * managing autoCommit, commit, and rollback.
     *
     * <p>Use this when executing inside an already-open transaction (e.g. inside a
     * {@code TransactionBlock}), or when you want to compose chains manually.
     */
    T execute(final Connection con) throws SQLException;

    /**
     * Executes all steps asynchronously, <b>wrapped in a transaction</b>.
     * Commits on success, rolls back on any exception.
     *
     * <p>This is the primary entry point for standalone chain execution.
     * Equivalent to {@code db.transaction(chain)}.
     */
    default CompletableFuture<T> execute(final SqlConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = connection.connection()) {
                con.setAutoCommit(false);
                try {
                    T result = execute(con);
                    con.commit();
                    return result;
                } catch (Exception e) {
                    try { con.rollback(); } catch (SQLException rbEx) { LOG.warning("Rollback failed: " + rbEx.getMessage()); }
                    throw new CompletionException(e);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, connection.executor());
    }

    /**
     * Executes all steps asynchronously <b>without</b> wrapping in a transaction.
     * Each query runs with autoCommit=true (the JDBC default).
     *
     * <p>Use this when you intentionally want non-transactional, auto-committed execution.
     */
    default CompletableFuture<T> executeAutoCommit(final SqlConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = connection.connection()) {
                return execute(con);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, connection.executor());
    }
}
