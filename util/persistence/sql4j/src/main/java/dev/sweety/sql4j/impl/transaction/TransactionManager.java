package dev.sweety.sql4j.impl.transaction;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.chain.QueryChain;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class TransactionManager {

    private final SqlConnection sqlConnection;

    public TransactionManager(SqlConnection sqlConnection) {
        this.sqlConnection = sqlConnection;
    }

    // --- Functional interface for block-style transactions ---

    @FunctionalInterface
    public interface TransactionBlock {
        void run(Transaction tx) throws SQLException;
    }

    // --- Transaction context ---

    /**
     * Wraps a JDBC {@link Connection} in a transaction context.
     *
     * <p>Provides typed query execution and named savepoint management.
     * The connection is managed externally by {@link TransactionManager} — do not close it here.
     *
     * <p>Usage:
     * <pre>{@code
     * db.transaction(tx -> {
     *     var result = tx.execute(repo.insert(entity));
     *     tx.savepoint("checkpoint");
     *     try {
     *         tx.execute(riskyQuery);
     *     } catch (SQLException e) {
     *         tx.rollbackTo("checkpoint");
     *     }
     * });
     * }</pre>
     */
    public static final class Transaction {

        private final Connection con;
        private final Map<String, Savepoint> savepoints = new LinkedHashMap<>();

        public Transaction(Connection con) {
            this.con = con;
        }

        /** Executes a query within this transaction. */
        public <T> T execute(Query<T> query) throws SQLException {
            return SqlRunner.execute(con, query);
        }

        /**
         * Creates a named savepoint at the current point in the transaction.
         * If a savepoint with this name already exists, it is replaced.
         */
        public void savepoint(String name) throws SQLException {
            Savepoint sp = con.setSavepoint(name);
            savepoints.put(name, sp);
        }

        /**
         * Rolls back to a previously created named savepoint.
         * Work done after the savepoint is undone; work before it is preserved.
         *
         * @throws SQLException if the savepoint does not exist or rollback fails
         */
        public void rollbackTo(String name) throws SQLException {
            Savepoint sp = savepoints.get(name);
            if (sp == null) throw new SQLException(
                    "Savepoint '" + name + "' not found. Available: " + savepoints.keySet());
            con.rollback(sp);
        }

        /**
         * Releases a named savepoint, freeing its resources.
         * After release, {@link #rollbackTo(String)} for this name will fail.
         */
        public void releaseSavepoint(String name) throws SQLException {
            Savepoint sp = savepoints.remove(name);
            if (sp != null) con.releaseSavepoint(sp);
        }

        /** Returns the underlying JDBC connection for advanced use cases. */
        public Connection connection() {
            return con;
        }
    }

    // --- Transaction execution methods ---

    /**
     * Executes a {@link QueryChain} within a single transaction managed by this manager.
     * Calls {@code chain.execute(con)} directly to avoid double transaction nesting
     * (since {@link QueryChain#execute(SqlConnection)} is already transactional by default).
     */
    public <T> CompletableFuture<T> transaction(final QueryChain<T> chain) {
        return CompletableFuture.supplyAsync(() -> {
            try (final Connection con = sqlConnection.connection()) {
                con.setAutoCommit(false);
                try {
                    T result = chain.execute(con); // raw — TM owns the transaction
                    con.commit();
                    return result;
                } catch (final Exception e) {
                    safeRollback(con);
                    throw new CompletionException(e);
                }
            } catch (final SQLException e) {
                throw new CompletionException(e);
            }
        }, sqlConnection.executor());
    }

    /**
     * Executes a {@link TransactionBlock} within a single transaction.
     * The block receives a {@link Transaction} context with savepoint support.
     * Commits on success, rolls back on any exception.
     */
    public CompletableFuture<Void> transaction(final TransactionBlock block) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = sqlConnection.connection()) {
                con.setAutoCommit(false);
                Transaction tx = new Transaction(con);
                try {
                    block.run(tx);
                    con.commit();
                } catch (SQLException | RuntimeException e) {
                    safeRollback(con);
                    throw new CompletionException(e);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, sqlConnection.executor());
    }

    private static void safeRollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ex) {
            SqlRunner.getLogger().log("Failed to rollback transaction: %s", ex.getMessage());
        }
    }
}
