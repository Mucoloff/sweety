package dev.sweety.core.persistence.sql;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import lombok.Getter;

import java.sql.SQLException;
import java.util.function.Function;

@Getter
public enum ConnectionType {
    SqLite(path -> {
        String dbPath = path[0].endsWith(".db") ? path[0] : path[0] + ".db";
        return new JdbcConnectionSource("jdbc:sqlite:" + dbPath);
    }),
    MySql(params -> new JdbcConnectionSource("jdbc:mysql://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5])),
    MariaDb(params -> new JdbcConnectionSource("jdbc:mariadb://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5]));

    public static final ConnectionType[] VALUES = values();

    private final ConnectionSupplier connectionSupplier;

    private JdbcConnectionSource connection;

    ConnectionType(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    public JdbcConnectionSource getConnection(String... params) {
        if (connection != null) return connection;
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