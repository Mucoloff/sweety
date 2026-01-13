package dev.sweety.sql4j.api.query.functions;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface QueryExecutor<T> {
    T execute(PreparedStatement ps) throws SQLException;
}
