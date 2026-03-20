package dev.sweety.sql4j.impl.connection;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.impl.configuration.*;
import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.provider.DriverManagerConnectionProvider;
import dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider;

import java.util.concurrent.Executor;

public enum ConnectionType {
    SQLITE,
    H2,
    MYSQL,
    MARIADB,
    POSTGRESQL;

    public SqlConnection create(final DatabaseConfig config, final Executor executor, final boolean useHikari) {
        if (config.dialectType().name().equals(this.name())) {
            final ConnectionProvider provider = useHikari
                    ? new HikariConnectionProvider(config)
                    : new DriverManagerConnectionProvider(config);
            return new SqlConnection(config.dialectType(), provider, executor);
        }
        throw new IllegalArgumentException("Config dialect " + config.dialectType() + " does not match " + this);
    }

    public SqlConnection create(final DatabaseConfig config, final Executor executor) {
        return create(config, executor, true); // Default to Hikari
    }
    
    // Backward compatibility
    public SqlConnection create(final Executor executor, final String... params) {
        final DatabaseConfig config = switch (this) {
            case SQLITE -> new SQLiteConfig(params[0]);
            case H2 -> new H2Config(params[0], params.length > 1 ? params[1] : "sa", params.length > 2 ? params[2] : "");
            case MYSQL -> new MySQLConfig(params[1], Integer.parseInt(params[2]), params[0], params[3], params[4], params.length > 5 ? params[5] : null);
            case MARIADB -> new MariaDBConfig(params[1], Integer.parseInt(params[2]), params[0], params[3], params[4], params.length > 5 ? params[5] : null);
            case POSTGRESQL -> new PostgreSQLConfig(params[1], Integer.parseInt(params[2]), params[0], params[3], params[4], params.length > 5 ? params[5] : null);
        };
        // Default to DriverManager for backward compatibility? Or upgrade to Hikari?
        // Let's use DriverManager as it was the old behavior.
        return create(config, executor, false);
    }
}