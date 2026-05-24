package dev.sweety.sql4j.impl.connection.provider;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.api.exception.Sql4jConnectionException;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

import java.sql.Connection;
import java.sql.SQLException;

public class HikariConnectionProvider implements ConnectionProvider {

    private final HikariDataSource dataSource;

    public HikariConnectionProvider(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.user());
        hikariConfig.setPassword(config.password());
        applyDialectTuning(hikariConfig, config.dialectType());
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public HikariConnectionProvider(HikariConfig hikariConfig) {
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Creates a provider from a fully-resolved {@link SQL4JConfig}, applying all
     * {@link SQL4JConfig.HikariTuning} parameters when present.
     */
    public HikariConnectionProvider(SQL4JConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.user());
        hikariConfig.setPassword(config.password());

        SQL4JConfig.HikariTuning tuning = config.hikariTuning();
        if (tuning != null) {
            hikariConfig.setMaximumPoolSize(tuning.maxPoolSize());
            hikariConfig.setMinimumIdle(tuning.minIdle());
            hikariConfig.setConnectionTimeout(tuning.connectionTimeout().toMillis());
            hikariConfig.setIdleTimeout(tuning.idleTimeout().toMillis());
            hikariConfig.setMaxLifetime(tuning.maxLifetime().toMillis());
        }

        applyDialectTuning(hikariConfig, config.dialect());
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public Connection get() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private static void applyDialectTuning(HikariConfig cfg, DialectType dialect) {
        switch (dialect) {
            case MYSQL, MARIADB -> {
                cfg.addDataSourceProperty("cachePrepStmts", "true");
                cfg.addDataSourceProperty("prepStmtCacheSize", "250");
                cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                cfg.addDataSourceProperty("useServerPrepStmts", "true");
            }
            case POSTGRESQL -> {
                // PgJDBC: server-side PS from first execution (default is 5).
                // Dramatically reduces parse/plan overhead for repeated queries.
                cfg.addDataSourceProperty("prepareThreshold", "1");
                cfg.addDataSourceProperty("preparedStatementCacheQueries", "250");
                cfg.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
            }
            case SQLITE -> cfg.setConnectionInitSql("PRAGMA foreign_keys = ON");
            default -> {}
        }
    }
}

