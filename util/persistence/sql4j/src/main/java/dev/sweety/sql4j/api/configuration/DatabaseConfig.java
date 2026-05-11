package dev.sweety.sql4j.api.configuration;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.exception.Sql4jConnectionException;
import dev.sweety.sql4j.impl.configuration.DatabaseConfigs;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import org.jetbrains.annotations.Nullable;

/**
 * Describes the connection parameters for a SQL4J-managed database.
 *
 * <h3>Usage</h3>
 * <p>Prefer the {@link SQL4J} step builder for creating instances.
 * Static factory methods on this interface are provided for legacy / programmatic use.
 *
 * <pre>{@code
 * DatabaseConfig cfg = DatabaseConfig.mysql("localhost", 3306, "mydb", "root", "secret");
 * }</pre>
 */
public interface DatabaseConfig {

    /** @return the dialect identifier for this configuration */
    DialectType dialectType();

    /** @return the fully-formed JDBC URL */
    String jdbcUrl();

    /** @return the database user name, or {@code null} if not required (e.g. SQLite) */
    @Nullable
    default String user() {
        return null;
    }

    /** @return the database password, or {@code null} if not required */
    @Nullable
    default String password() {
        return null;
    }

    // ─── Static factories ────────────────────────────────────────────────────────

    static DatabaseConfig sqlite(final String path) {
        return DatabaseConfigs.sqlite(path);
    }

    static DatabaseConfig h2(final String path, final String user, final String password) {
        return DatabaseConfigs.h2(path, user, password);
    }

    static DatabaseConfig mysql(final String host, final int port, final String database,
                                final String user, final String password) {
        return mysql(host, port, database, user, password, null);
    }

    static DatabaseConfig mysql(final String host, final int port, final String database,
                                final String user, final String password,
                                @Nullable final String properties) {
        return DatabaseConfigs.mysql(host, port, database, user, password, properties);
    }

    static DatabaseConfig mariadb(final String host, final int port, final String database,
                                  final String user, final String password) {
        return mariadb(host, port, database, user, password, null);
    }

    static DatabaseConfig mariadb(final String host, final int port, final String database,
                                  final String user, final String password,
                                  @Nullable final String properties) {
        return DatabaseConfigs.mariadb(host, port, database, user, password, properties);
    }

    static DatabaseConfig postgresql(final String host, final int port, final String database,
                                     final String user, final String password) {
        return postgresql(host, port, database, user, password, null);
    }

    static DatabaseConfig postgresql(final String host, final int port, final String database,
                                     final String user, final String password,
                                     @Nullable final String properties) {
        return DatabaseConfigs.postgresql(host, port, database, user, password, properties);
    }

    /**
     * Creates a {@link DatabaseConfig} from a dialect identifier and positional parameters.
     * Parameter order: {@code host, port, database, user, password[, properties]} for network
     * dialects; {@code path} for SQLite; {@code path, user, password} for H2.
     */
    static DatabaseConfig of(final DialectType dialectType, final String... params) {
        if (dialectType == null) throw new Sql4jConnectionException("dialectType cannot be null");
        if (params == null) throw new Sql4jConnectionException("params cannot be null");

        return switch (dialectType) {
            case SQLITE -> {
                requireExactParams(dialectType, params, 1);
                yield sqlite(params[0]);
            }
            case H2 -> {
                requireExactParams(dialectType, params, 3);
                yield h2(params[0], params[1], params[2]);
            }
            case MYSQL, MARIADB, POSTGRESQL -> {
                requireParamRange(dialectType, params, 5, 6);
                yield networkConfig(dialectType,
                        params[0], parsePort(dialectType, params[1]),
                        params[2], params[3], params[4],
                        params.length > 5 ? params[5] : null);
            }
        };
    }

    /**
     * Legacy parameter order: {@code database, host, port, user, password[, properties]}.
     *
     * @deprecated Use {@link #of(DialectType, String...)} with the standard parameter order.
     */
    @Deprecated
    static DatabaseConfig ofLegacy(final DialectType dialectType, final String... params) {
        if (dialectType == null) throw new Sql4jConnectionException("dialectType cannot be null");
        if (params == null) throw new Sql4jConnectionException("params cannot be null");

        return switch (dialectType) {
            case SQLITE -> {
                requireExactParams(dialectType, params, 1);
                yield sqlite(params[0]);
            }
            case H2 -> {
                requireParamRange(dialectType, params, 1, 3);
                yield h2(params[0],
                        params.length > 1 && params[1] != null ? params[1] : "sa",
                        params.length > 2 && params[2] != null ? params[2] : "");
            }
            case MYSQL, MARIADB, POSTGRESQL -> {
                requireParamRange(dialectType, params, 5, 6);
                yield networkConfig(dialectType,
                        params[1], parsePort(dialectType, params[2]),
                        params[0], params[3], params[4],
                        params.length > 5 ? params[5] : null);
            }
        };
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private static DatabaseConfig networkConfig(DialectType dialectType, String host, int port,
                                                String database, String user, String password,
                                                @Nullable String properties) {
        return switch (dialectType) {
            case MYSQL -> mysql(host, port, database, user, password, properties);
            case MARIADB -> mariadb(host, port, database, user, password, properties);
            case POSTGRESQL -> postgresql(host, port, database, user, password, properties);
            default -> throw new Sql4jConnectionException("Unsupported network dialect: " + dialectType);
        };
    }

    private static void requireExactParams(DialectType dt, String[] params, int expected) {
        if (params.length != expected)
            throw new Sql4jConnectionException("Invalid parameters for " + dt +
                    ": expected " + expected + ", got " + params.length);
    }

    private static void requireParamRange(DialectType dt, String[] params, int min, int max) {
        if (params.length < min || params.length > max)
            throw new Sql4jConnectionException("Invalid parameters for " + dt +
                    ": expected between " + min + " and " + max + ", got " + params.length);
    }

    private static int parsePort(DialectType dt, String raw) {
        if (raw == null || raw.isBlank())
            throw new Sql4jConnectionException("Invalid port for " + dt + ": value cannot be null or blank");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new Sql4jConnectionException("Invalid port for " + dt + ": '" + raw + "'", e);
        }
    }
}
