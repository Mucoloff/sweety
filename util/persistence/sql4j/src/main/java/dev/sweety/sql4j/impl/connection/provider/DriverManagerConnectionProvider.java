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

    @Deprecated
    public DriverManagerConnectionProvider(DatabaseConfig config) {
        this.jdbcUrl = config.jdbcUrl();
        this.user = config.user();
        this.password = config.password();
        this.dialectType = config.dialectType();
    }

    @Deprecated
    public DriverManagerConnectionProvider(SQL4JConfig config) {
        this.jdbcUrl = config.jdbcUrl();
        this.user = config.user();
        this.password = config.password();
        this.dialectType = config.dialect();
    }

    @Deprecated
    public DriverManagerConnectionProvider(String jdbcUrl, @Nullable String user, @Nullable String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) throw new IllegalArgumentException("jdbcUrl must not be null or blank");
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.dialectType = null;
    }

    public static DriverManagerConnectionProvider of(DatabaseConfig config) {
        return new DriverManagerConnectionProvider(config);
    }

    public static DriverManagerConnectionProvider of(SQL4JConfig config) {
        return new DriverManagerConnectionProvider(config);
    }

    public static DriverManagerConnectionProvider of(String jdbcUrl, @Nullable String user,
                                                     @Nullable String password) {
        return new DriverManagerConnectionProvider(jdbcUrl, user, password);
    }

    @Override
    public Connection get() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
        if (dialectType == DialectType.SQLITE) {
            // WAL + busy_timeout are carried in the sqlite JDBC URL (see SQLiteConfig#jdbcUrl).
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

