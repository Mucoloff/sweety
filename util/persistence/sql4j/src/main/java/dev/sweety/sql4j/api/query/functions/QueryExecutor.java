package dev.sweety.sql4j.api.query.functions;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface QueryExecutor<T> extends QueryBinder {
    T execute(PreparedStatement ps) throws SQLException;

    @Override
    default void bind(PreparedStatement ps) throws SQLException {
        execute(ps);
    }

    QueryExecutor<?> EMPTY = _ -> null;
}
