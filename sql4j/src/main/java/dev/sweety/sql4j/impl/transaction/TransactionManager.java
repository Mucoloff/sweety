package dev.sweety.sql4j.impl.transaction;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class TransactionManager {

    private final SqlConnection sqlConnection;

    public TransactionManager(SqlConnection sqlConnection) {
        this.sqlConnection = sqlConnection;
    }

    /**
     * Interfaccia per eseguire le query all’interno del blocco di transazione
     */
    @FunctionalInterface
    public interface TransactionBlock {
        void run(Transaction tx) throws SQLException;
    }

    /**
     * Wrapper che esegue le query usando la connection della transazione
     */
    public static class Transaction {
        private final Connection con;

        public Transaction(final Connection con) {
            this.con = con;
        }

        public <T> T execute(Query<T> query) throws SQLException {
            final String sql = query.sql();
            try (PreparedStatement ps = con.prepareStatement(sql,
                    query.returnGeneratedKeys() ? PreparedStatement.RETURN_GENERATED_KEYS : PreparedStatement.NO_GENERATED_KEYS)) {

                query.bind(ps);
                System.out.println("Executing Query in Transaction: " + sql);
                return query.execute(ps);
            }
        }
    }

    /**
     * Esegue un blocco di transazione in modalità async
     */
    public CompletableFuture<Void> transaction(TransactionBlock block) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = sqlConnection.connection()) {
                con.setAutoCommit(false);
                Transaction tx = new Transaction(con);

                try {
                    block.run(tx); // esegue tutte le query
                    con.commit();
                } catch (SQLException | RuntimeException e) {
                    con.rollback();
                    throw new CompletionException(e);
                }

            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, SqlConnection.executor(sqlConnection.dialectType()));
    }
}
