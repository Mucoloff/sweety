package dev.sweety.sql4j.impl.connection;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.exception.Sql4jConnectionException;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.connection.provider.DriverManagerConnectionProvider;
import dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider;
import dev.sweety.thread.ThreadUtil;

import java.util.Objects;
import java.util.concurrent.Executor;

public enum ConnectionType {
    SQLITE(DialectType.SQLITE),
    H2(DialectType.H2),
    MYSQL(DialectType.MYSQL),
    MARIADB(DialectType.MARIADB),
    POSTGRESQL(DialectType.POSTGRESQL);

    private final DialectType dialectType;

    ConnectionType(final DialectType dialectType) {
        this.dialectType = dialectType;
    }

    public DialectType dialectType() {
        return dialectType;
    }

    public static ConnectionType fromDialectType(final DialectType dialectType) {
        final DialectType requiredDialectType = Objects.requireNonNull(dialectType, "dialectType cannot be null");
        return switch (requiredDialectType) {
            case SQLITE -> SQLITE;
            case H2 -> H2;
            case MYSQL -> MYSQL;
            case MARIADB -> MARIADB;
            case POSTGRESQL -> POSTGRESQL;
        };
    }

    public SqlConnection create(final DatabaseConfig config, final Executor executor, final boolean useHikari, final boolean ownsExecutor) {
        final DatabaseConfig requiredConfig = Objects.requireNonNull(config, "config cannot be null");
        final Executor requiredExecutor = Objects.requireNonNull(executor, "executor cannot be null");

        if (requiredConfig.dialectType() == this.dialectType) {
            final ConnectionProvider provider = useHikari
                    ? new HikariConnectionProvider(requiredConfig)
                    : new DriverManagerConnectionProvider(requiredConfig);
            return new SqlConnection(this.dialectType, provider, requiredExecutor, ownsExecutor);
        }
        throw new Sql4jConnectionException("Config dialect " + requiredConfig.dialectType() + " does not match ConnectionType." + this);
    }

    public SqlConnection create(final DatabaseConfig config, final Executor executor, final boolean useHikari) {
        return create(config, executor, useHikari, false);
    }

    public SqlConnection create(final DatabaseConfig config, final Executor executor) {
        return create(config, executor, true, false); // Default to Hikari, user owns executor
    }

    public SqlConnection create(final DatabaseConfig config, final boolean useHikari) {
        // Automatically create a cached thread pool and mark ownsExecutor = true
        // so that SqlConnection will shut it down on close().
        return create(config, ThreadUtil.cachedThreadPool("sql4j-connection"), useHikari, true);
    }

    public SqlConnection create(final DatabaseConfig config) {
        return create(config, true); // Default to Hikari, auto-manage executor
    }

    // Backward compatibility
    public SqlConnection create(final Executor executor, final String... params) {
        Objects.requireNonNull(executor, "executor cannot be null");
        final DatabaseConfig config = DatabaseConfig.ofLegacy(this.dialectType, params);
        // Default to DriverManager for backward compatibility? Or upgrade to Hikari?
        // Let's use DriverManager as it was the old behavior.
        return create(config, executor, false);
    }
}