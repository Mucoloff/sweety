package dev.sweety.sql4j.impl.configuration;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Factory methods that create concrete {@link DatabaseConfig} instances.
 *
 * <p>This class owns all knowledge of the concrete {@code *Config} record types so that
 * {@link DatabaseConfig} itself (an {@code api} interface) does not need to import
 * any {@code impl} types other than {@code DialectType}.
 */
public final class DatabaseConfigs {

    private DatabaseConfigs() {}

    public static DatabaseConfig sqlite(String path) {
        return new SQLiteConfig(path);
    }

    public static DatabaseConfig h2(String path, String user, String password) {
        return new H2Config(path, user, password);
    }

    public static DatabaseConfig mysql(String host, int port, String database,
                                       String user, String password,
                                       @Nullable String properties) {
        return new MySQLConfig(host, port, database, user, password, properties != null ? properties : "");
    }

    public static DatabaseConfig mariadb(String host, int port, String database,
                                         String user, String password,
                                         @Nullable String properties) {
        return new MariaDBConfig(host, port, database, user, password, properties != null ? properties : "");
    }

    public static DatabaseConfig postgresql(String host, int port, String database,
                                            String user, String password,
                                            @Nullable String properties) {
        return new PostgreSQLConfig(host, port, database, user, password, properties != null ? properties : "");
    }
}
