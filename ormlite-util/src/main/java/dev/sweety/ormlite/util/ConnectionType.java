package dev.sweety.ormlite.util;

import com.j256.ormlite.db.*;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import lombok.Getter;

import java.sql.SQLException;
import java.util.function.Function;

@Getter
public enum ConnectionType {
    SQLITE(path -> {
        String dbPath = path[0].endsWith(".db") ? path[0] : path[0] + ".db";
        return new JdbcConnectionSource("jdbc:sqlite:" + dbPath, new SqliteDatabaseType());
    }),
    H2(path -> {
        String dbPath = path[0].startsWith("mem:") ? path[0] : path[0] + ".mv.db";
        return new JdbcConnectionSource("jdbc:h2:" + dbPath, new H2DatabaseType());
    }),
    MYSQL(params -> new JdbcConnectionSource("jdbc:mysql://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5], new MysqlDatabaseType())),
    MARIADB(params -> new JdbcConnectionSource("jdbc:mariadb://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5], new MariaDbDatabaseType())),
    POSTGRESQL(params -> new JdbcConnectionSource("jdbc:postgresql://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5], new PostgresDatabaseType()))
    ;

    public static final ConnectionType[] VALUES = values();

    private final ConnectionSupplier connectionSupplier;

    private JdbcConnectionSource connection;

    ConnectionType(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    public JdbcConnectionSource getConnection(String... params) {
        if (connection != null && connection.isOpen("")) return connection;
        return connection = connectionSupplier.apply(params);
    }

    @FunctionalInterface
    private interface ConnectionSupplier extends Function<String[], JdbcConnectionSource> {
        JdbcConnectionSource get(String... params) throws SQLException;

        @Override
        default JdbcConnectionSource apply(String... params) {
            try {
                return get(params);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}