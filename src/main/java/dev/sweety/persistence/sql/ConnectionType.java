package dev.sweety.persistence.sql;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import dev.sweety.version.VersionComparison;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public enum ConnectionType {
    SqLite(path -> {
        try {
            String dbPath = path[0].endsWith(".db") ? path[0] : path[0] + ".db";
            return new JdbcConnectionSource("jdbc:sqlite:" + dbPath);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }),
    MySql(params -> {
        try {
            return new JdbcConnectionSource("jdbc:mysql://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5]);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }),
    MariaDb(params -> {
        try {
            return new JdbcConnectionSource("jdbc:mariadb://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5]);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    });

    public static final ConnectionType[] VALUES = values();

    private final Function<String[], JdbcConnectionSource> connectionSupplier;

    private JdbcConnectionSource connection;

    public JdbcConnectionSource getConnection(String... params) {
        if (connection != null) return connection;
        return connection = connectionSupplier.apply(params);
    }
}