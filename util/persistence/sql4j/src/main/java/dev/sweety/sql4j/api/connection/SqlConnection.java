package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.impl.connection.DialectType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.*;

public abstract class SqlConnection {

    private static Executor EXECUTOR;
    private static volatile boolean initialized = false;

    private final String database, user, password;
    private final DialectType dialectType;

    public SqlConnection(final String database, final String user, final String password, DialectType dialectType) {
        this.database = database;
        this.user = user;
        this.password = password;
        this.dialectType = dialectType;
    }

    public abstract String url();

    public Connection connection() throws SQLException {
        final Connection connection = DriverManager.getConnection(url(), this.user, this.password);
        if (this.dialectType.equals(DialectType.SQLITE)) {
            try (var st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }

        return connection;
    }

    public String database() {
        return this.database;
    }

    public DialectType dialectType() {
        return dialectType;
    }

    public Dialect dialect() {
        return dialectType.dialect();
    }


    public <T> CompletableFuture<T> executeAsync(Query<T> query) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try (final Connection con = connection()) {
                    return QueryExecutor.execute(con, query);
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, executor(this.dialectType));
        }catch (RejectedExecutionException e){
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void init() {
        if (initialized) return;
        Runtime.getRuntime().addShutdownHook(new Thread(SqlConnection::shutdownExecutor));
        initialized = true;
    }

    public static synchronized Executor executor(DialectType dialectType) {
        if (EXECUTOR == null) {
            init();
            int threads = dialectType == DialectType.SQLITE ? 1 : Runtime.getRuntime().availableProcessors();
            EXECUTOR = new ThreadPoolExecutor(
                    threads,
                    threads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(100),
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
        return EXECUTOR;
    }


    public static void shutdownExecutor() {
        if (EXECUTOR instanceof ExecutorService service) {
            service.shutdown();
            EXECUTOR = null;
        }
    }


}
