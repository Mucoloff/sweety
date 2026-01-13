package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class SqlConnection {

    private static Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private final String database, user, password;
    private Connection connection;

    public SqlConnection(final String database, final String user, final String password) {
        this.database = database;
        this.user = user;
        this.password = password;
    }

    public abstract String url();

    public Connection connection() throws SQLException {
        if (connection == null || connection.isClosed())
            this.connection = DriverManager.getConnection(url(), this.user, this.password);
        return connection;
    }

    public String database() {
        return this.database;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.close();
    }

    public <T> CompletableFuture<T> executeAsync(Query<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = connection();
                 PreparedStatement ps = con.prepareStatement(query.sql(), query.returnGeneratedKeys() ? PreparedStatement.RETURN_GENERATED_KEYS : PreparedStatement.NO_GENERATED_KEYS)) {

                System.out.println("Executing Query: " + query.sql());

                query.bind(ps);
                return query.execute(ps);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor());
    }

    public static Executor executor() {
        if (EXECUTOR == null) EXECUTOR = Executors.newSingleThreadExecutor();
        return EXECUTOR;
    }

    public static void shutdownExecutor() {
        if (EXECUTOR instanceof java.util.concurrent.ExecutorService service) {
            service.shutdown();
            EXECUTOR = null;
        }
    }


}
