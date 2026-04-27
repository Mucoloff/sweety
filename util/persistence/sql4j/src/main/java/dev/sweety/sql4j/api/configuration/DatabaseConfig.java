package dev.sweety.sql4j.api.configuration;

import dev.sweety.sql4j.impl.configuration.H2Config;
import dev.sweety.sql4j.impl.configuration.MariaDBConfig;
import dev.sweety.sql4j.impl.configuration.MySQLConfig;
import dev.sweety.sql4j.impl.configuration.PostgreSQLConfig;
import dev.sweety.sql4j.impl.configuration.SQLiteConfig;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

public interface DatabaseConfig {
    DialectType dialectType();

    String jdbcUrl();

    default String user() {
        return null;
    }

    default String password() {
        return null;
    }

    static DatabaseConfig sqlite(final String path) {
        return new SQLiteConfig(path);
    }

    static DatabaseConfig h2(final String path, final String user, final String password) {
        return new H2Config(path, user, password);
    }

    static DatabaseConfig mysql(final String host, final int port, final String database, final String user, final String password) {
        return mysql(host, port, database, user, password, null);
    }

    static DatabaseConfig mysql(final String host, final int port, final String database, final String user, final String password, final String properties) {
        return new MySQLConfig(host, port, database, user, password, properties);
    }

    static DatabaseConfig mariadb(final String host, final int port, final String database, final String user, final String password) {
        return mariadb(host, port, database, user, password, null);
    }

    static DatabaseConfig mariadb(final String host, final int port, final String database, final String user, final String password, final String properties) {
        return new MariaDBConfig(host, port, database, user, password, properties);
    }

    static DatabaseConfig postgresql(final String host, final int port, final String database, final String user, final String password) {
        return postgresql(host, port, database, user, password, null);
    }

    static DatabaseConfig postgresql(final String host, final int port, final String database, final String user, final String password, final String properties) {
        return new PostgreSQLConfig(host, port, database, user, password, properties);
    }

    static DatabaseConfig of(final DialectType dialectType, final String... params) {
        if (dialectType == null) {
            throw new IllegalArgumentException("dialectType cannot be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }

        return switch (dialectType) {
            case SQLITE -> {
                requireExactParams(dialectType, params, 1);
                yield sqlite(requiredParam(dialectType, params, 0, "path"));
            }
            case H2 -> {
                requireExactParams(dialectType, params, 3);
                yield h2(
                        requiredParam(dialectType, params, 0, "path"),
                        requiredParam(dialectType, params, 1, "user"),
                        requiredParam(dialectType, params, 2, "password")
                );
            }
            case MYSQL, MARIADB, POSTGRESQL -> {
                requireParamRange(dialectType, params, 5, 6);
                yield networkConfig(
                        dialectType,
                        requiredParam(dialectType, params, 0, "host"),
                        requiredIntParam(dialectType, params, 1, "port"),
                        requiredParam(dialectType, params, 2, "database"),
                        requiredParam(dialectType, params, 3, "user"),
                        requiredParam(dialectType, params, 4, "password"),
                        optionalParam(params, 5)
                );
            }
        };
    }

    // Legacy order for network databases is: database, host, port, user, password, [properties]
    static DatabaseConfig ofLegacy(final DialectType dialectType, final String... params) {
        if (dialectType == null) {
            throw new IllegalArgumentException("dialectType cannot be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }

        return switch (dialectType) {
            case SQLITE -> {
                requireExactParams(dialectType, params, 1);
                yield sqlite(requiredParam(dialectType, params, 0, "path"));
            }
            case H2 -> {
                requireParamRange(dialectType, params, 1, 3);
                yield h2(
                        requiredParam(dialectType, params, 0, "path"),
                        optionalParamOrDefault(params, 1, "sa"),
                        optionalParamOrDefault(params, 2, "")
                );
            }
            case MYSQL, MARIADB, POSTGRESQL -> {
                requireParamRange(dialectType, params, 5, 6);
                yield networkConfig(
                        dialectType,
                        requiredParam(dialectType, params, 1, "host"),
                        requiredIntParam(dialectType, params, 2, "port"),
                        requiredParam(dialectType, params, 0, "database"),
                        requiredParam(dialectType, params, 3, "user"),
                        requiredParam(dialectType, params, 4, "password"),
                        optionalParam(params, 5)
                );
            }
        };
    }

    private static DatabaseConfig networkConfig(
            final DialectType dialectType,
            final String host,
            final int port,
            final String database,
            final String user,
            final String password,
            final String properties
    ) {
        return switch (dialectType) {
            case MYSQL -> mysql(host, port, database, user, password, properties);
            case MARIADB -> mariadb(host, port, database, user, password, properties);
            case POSTGRESQL -> postgresql(host, port, database, user, password, properties);
            default -> throw new IllegalArgumentException("Unsupported network dialect: " + dialectType);
        };
    }

    private static void requireExactParams(final DialectType dialectType, final String[] params, final int expected) {
        if (params.length != expected) {
            throw new IllegalArgumentException("Invalid parameters for " + dialectType + ": expected " + expected + ", got " + params.length);
        }
    }

    private static void requireParamRange(final DialectType dialectType, final String[] params, final int min, final int max) {
        if (params.length < min || params.length > max) {
            throw new IllegalArgumentException("Invalid parameters for " + dialectType + ": expected between " + min + " and " + max + ", got " + params.length);
        }
    }

    private static String requiredParam(final DialectType dialectType, final String[] params, final int index, final String name) {
        final String value = params[index];
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid parameter '" + name + "' for " + dialectType + ": value cannot be null or blank");
        return value;
    }

    private static int requiredIntParam(final DialectType dialectType, final String[] params, final int index, final String name) {
        final String value = params[index];
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid parameter '" + name + "' for " + dialectType + ": value cannot be null or blank");
        return parseIntParam(dialectType, value, name);
    }

    private static String optionalParam(final String[] params, final int index) {
        return params.length > index ? params[index] : null;
    }

    private static String optionalParamOrDefault(final String[] params, final int index, final String defaultValue) {
        return params.length > index && params[index] != null ? params[index] : defaultValue;
    }

    private static int parseIntParam(final DialectType dialectType, final String rawParam, String name) {
        try {
            return Integer.parseInt(rawParam);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + name + " for " + dialectType + ": '" + rawParam + "'", exception);
        }
    }
}
