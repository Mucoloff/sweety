package dev.sweety.sql4j.impl.connection.provider;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DriverManagerConnectionProvider implements ConnectionProvider {

    private final DatabaseConfig config;

    public DriverManagerConnectionProvider(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public Connection get() throws SQLException {
        Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
        if (config.dialectType() == DialectType.SQLITE) {
            try (var st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    @Override
    public void close() {
        // No-op
    }
}

