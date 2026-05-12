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

/**
 * A configured SQL4J connection that bridges a JDBC {@link ConnectionProvider} with
 * an async {@link Executor}.
 *
 * <p>Obtain an instance via the step builder:
 * <pre>{@code
 * Database db = SQL4J.connect().mysql("localhost", 3306, "mydb", "user", "pass").open();
 * SqlConnection con = db.getConnection();
 * }</pre>
 *
 * <p>{@code SqlConnection} is {@link AutoCloseable}: closing it shuts down the underlying
 * connection pool and (if {@code ownsExecutor} is {@code true}) the executor thread pool.
 */
public class SqlConnection implements AutoCloseable {

    private final DialectType dialectType;
    private final ConnectionProvider connectionProvider;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final List<QueryInterceptor> interceptors = new ArrayList<>();

    /**
     * Creates a {@code SqlConnection} where the caller owns the executor lifecycle.
     *
     * @param dialectType        the database dialect
     * @param connectionProvider the JDBC connection source (e.g. HikariCP pool)
     * @param executor           the executor for async query dispatch
     */
    public SqlConnection(final DialectType dialectType, final ConnectionProvider connectionProvider, final Executor executor) {
        this(dialectType, connectionProvider, executor, false);
    }

    /**
     * Creates a {@code SqlConnection}.
     *
     * @param dialectType        the database dialect
     * @param connectionProvider the JDBC connection source
     * @param executor           the executor for async query dispatch
     * @param ownsExecutor       {@code true} if this instance should shut the executor down on {@link #close()}
     */
    public SqlConnection(final DialectType dialectType, final ConnectionProvider connectionProvider, final Executor executor, final boolean ownsExecutor) {
        this.dialectType = Objects.requireNonNull(dialectType, "dialectType cannot be null");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.ownsExecutor = ownsExecutor;
    }

    /**
     * Borrows a raw JDBC {@link Connection} from the underlying pool.
     * <strong>The caller is responsible for closing it</strong> (returning it to the pool).
     *
     * @return an open JDBC connection
     * @throws SQLException if a connection cannot be acquired
     */
    public Connection connection() throws SQLException {
        return connectionProvider.get();
    }

    /**
     * Returns the dialect type configured for this connection.
     *
     * @return the non-null {@link DialectType}
     */
    public DialectType dialectType() {
        return dialectType;
    }

    /**
     * Returns the SQL dialect strategy used for query generation.
     *
     * @return the non-null {@link Dialect} for the configured database type
     */
    public Dialect dialect() {
        return dialectType.dialect();
    }

    /**
     * Returns the {@link Executor} used for asynchronous query dispatch.
     *
     * @return the non-null executor
     */
    public Executor executor() {
        return executor;
    }

    /**
     * Executes a {@link Query} asynchronously by borrowing a JDBC connection from the pool,
     * delegating to {@link dev.sweety.sql4j.api.connection.SqlRunner#execute}, and returning
     * the connection when done.
     *
     * @param <T>   the result type
     * @param query the query to execute
     * @return a {@link CompletableFuture} completing with the query result on {@link #executor()}
     */
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
