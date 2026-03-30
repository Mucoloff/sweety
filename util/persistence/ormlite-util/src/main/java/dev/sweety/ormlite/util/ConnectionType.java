package dev.sweety.ormlite.util;

import com.j256.ormlite.db.*;
import com.j256.ormlite.jdbc.JdbcConnectionSource;

import java.sql.SQLException;

public enum ConnectionType {
    SQLITE,
    H2,
    MYSQL,
    MARIADB,
    POSTGRESQL;

    public static final ConnectionType[] VALUES = values();

    private JdbcConnectionSource connection;

    public JdbcConnectionSource create(final String... params) {
        try {
            return switch (this) {
                case SQLITE -> new JdbcConnectionSource("jdbc:sqlite:" + toSqlitePath(params), new SqliteDatabaseType());
                case H2 -> new JdbcConnectionSource("jdbc:h2:" + toH2Path(params), new H2DatabaseType());
                case MYSQL -> createJdbc("mysql", params, new MysqlDatabaseType());
                case MARIADB -> createJdbc("mariadb", params, new MariaDbDatabaseType());
                case POSTGRESQL -> createJdbc("postgresql", params, new PostgresDatabaseType());
            };
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public JdbcConnectionSource getConnection(String... params) {
        if (connection != null && connection.isOpen("")) return connection;
        return connection = create(params);
    }

    private static JdbcConnectionSource createJdbc(final String dialect,
                                                   final String[] params,
                                                   final DatabaseType databaseType) throws SQLException {
        requireSize(params, 6, dialect);
        final String url = "jdbc:" + dialect + "://" + params[1] + ":" + params[2] + "/" + params[0] + optional(params[3]);
        return new JdbcConnectionSource(url, params[4], params[5], databaseType);
    }

    private static String toSqlitePath(final String[] params) {
        requireSize(params, 1, "sqlite");
        return params[0].endsWith(".db") ? params[0] : params[0] + ".db";
    }

    private static String toH2Path(final String[] params) {
        requireSize(params, 1, "h2");
        return params[0].startsWith("mem:") ? params[0] : params[0] + ".mv.db";
    }

    private static String optional(final String value) {
        return value == null ? "" : value;
    }

    private static void requireSize(final String[] params, final int minSize, final String dialect) {
        if (params == null || params.length < minSize) {
            throw new IllegalArgumentException("Not enough params for " + dialect + ": expected at least " + minSize);
        }
    }
}