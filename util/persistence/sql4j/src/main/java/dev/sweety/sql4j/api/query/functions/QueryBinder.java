package dev.sweety.sql4j.api.query.functions;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface QueryBinder {
    void bind(PreparedStatement ps) throws SQLException;
}
