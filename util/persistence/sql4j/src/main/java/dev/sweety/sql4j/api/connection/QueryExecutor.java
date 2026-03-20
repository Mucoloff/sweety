package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class QueryExecutor {

    private QueryExecutor() {}

    public static <T> T execute(Connection con, Query<T> query) throws SQLException {
        final String sql = query.sql();
        try (PreparedStatement ps = con.prepareStatement(
                sql,
                query.returnGeneratedKeys()
                        ? PreparedStatement.RETURN_GENERATED_KEYS
                        : PreparedStatement.NO_GENERATED_KEYS)) {

            System.out.println("[SQL4J] Executing SQL: " + sql);
            query.bind(ps);
            return query.execute(ps);
        }
    }
}
