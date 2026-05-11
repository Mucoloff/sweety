package dev.sweety.sql4j.impl.connection.provider;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DriverManagerConnectionProvider implements ConnectionProvider {

    private final String jdbcUrl;
    @Nullable private final String user;
    @Nullable private final String password;
    private final DialectType dialectType;

    public DriverManagerConnectionProvider(DatabaseConfig config) {
        this.jdbcUrl = config.jdbcUrl();
        this.user = config.user();
        this.password = config.password();
        this.dialectType = config.dialectType();
    }

    public DriverManagerConnectionProvider(SQL4JConfig config) {
        this.jdbcUrl = config.jdbcUrl();
        this.user = config.user();
        this.password = config.password();
        this.dialectType = config.dialect();
    }

    public DriverManagerConnectionProvider(String jdbcUrl, @Nullable String user, @Nullable String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.dialectType = null;
    }

    @Override
    public Connection get() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
        if (dialectType == DialectType.SQLITE) {
            try (var st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    @Override
    public void close() {
        // No-op for DriverManager connections
    }
}

