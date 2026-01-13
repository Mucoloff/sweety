package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.mysql.MySQLConnection;
import dev.sweety.sql4j.impl.connection.mysql.maria.MariaDBConnection;
import dev.sweety.sql4j.impl.connection.sqlite.SQLiteConnection;
import lombok.Getter;

import java.sql.SQLException;
import java.util.function.Function;

@Getter
public enum ConnectionType {
    SQLITE(path -> new SQLiteConnection(path[0])),
    /*
    H2(path -> {
        String dbPath = path[0].startsWith("mem:") ? path[0] : path[0] + ".mv.db";
        return new JdbcConnectionSource("jdbc:h2:" + dbPath, new H2DatabaseType());
    }),
     */
    MYSQL(params -> new MySQLConnection(params[1] ,params[2],params[0], params[3], params[4], params[5])),
    MARIADB(params -> new MariaDBConnection(params[1] ,params[2],params[0], params[3], params[4], params[5])),
    //POSTGRESQL(params -> new JdbcConnectionSource("jdbc:postgresql://" + params[1] + ":" + params[2] + "/" + params[0] + params[3], params[4], params[5], new PostgresDatabaseType())),
    ;

    public static final ConnectionType[] VALUES = values();

    private final ConnectionSupplier connectionSupplier;

    private SqlConnection connection;

    ConnectionType(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    public SqlConnection getConnection(String... params) {
        if (connection != null) return connection;
        return connection = connectionSupplier.apply(params);
    }

    @FunctionalInterface
    private interface ConnectionSupplier extends Function<String[], SqlConnection> {
        SqlConnection get(String... params) throws SQLException;

        @Override
        default SqlConnection apply(String... params) {
            try {
                return get(params);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}