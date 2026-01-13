package dev.sweety.sql4j.api.query;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public sealed interface Query<T> permits AbstractQuery, UnsafeQuery {

    void bind(final PreparedStatement ps) throws SQLException;

    T execute(final PreparedStatement ps) throws SQLException;

    String sql();

    default boolean returnGeneratedKeys() {
        return false;
    }
}
