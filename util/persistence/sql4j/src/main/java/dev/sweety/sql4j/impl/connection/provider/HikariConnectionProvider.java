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

        // Default settings
        if (config.dialectType() == DialectType.MYSQL || config.dialectType() == DialectType.MARIADB) {
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        if (config.dialectType() == DialectType.SQLITE) {
            hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON");
        }

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

        if (config.dialect() == DialectType.MYSQL || config.dialect() == DialectType.MARIADB) {
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        if (config.dialect() == DialectType.SQLITE) {
            hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON");
        }

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
}

