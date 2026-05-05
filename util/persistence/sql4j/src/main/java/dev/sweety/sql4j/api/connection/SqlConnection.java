package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.api.interceptor.QueryInterceptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class SqlConnection implements AutoCloseable {

    private final DialectType dialectType;
    private final ConnectionProvider connectionProvider;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final List<QueryInterceptor> interceptors = new ArrayList<>();

    public SqlConnection(final DialectType dialectType, final ConnectionProvider connectionProvider, final Executor executor) {
        this(dialectType, connectionProvider, executor, false);
    }

    public SqlConnection(final DialectType dialectType, final ConnectionProvider connectionProvider, final Executor executor, final boolean ownsExecutor) {
        this.dialectType = Objects.requireNonNull(dialectType, "dialectType cannot be null");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.ownsExecutor = ownsExecutor;
    }

    public Connection connection() throws SQLException {
        return connectionProvider.get();
    }

    public DialectType dialectType() {
        return dialectType;
    }

    public Dialect dialect() {
        return dialectType.dialect();
    }

    public Executor executor() {
        return executor;
    }

    public <T> CompletableFuture<T> executeAsync(Query<T> query) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try (final Connection con = connection()) {
                    return SqlRunner.execute(con, query, interceptors);
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public void addInterceptor(QueryInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor));
    }

    public List<QueryInterceptor> interceptors() {
        return interceptors;
    }

    @Override
    public void close() {
        connectionProvider.close();
        if (ownsExecutor && executor instanceof ExecutorService es) {
            es.shutdown();
        }
    }
}
