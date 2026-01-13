package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public sealed interface Query<T> permits AbstractQuery, UnsafeQuery {

    void bind(final PreparedStatement ps) throws SQLException;

    T execute(final PreparedStatement ps) throws SQLException;

    String sql();

    default boolean returnGeneratedKeys() {
        return false;
    }

    default CompletableFuture<T> execute(final SqlConnection connection) {
        return connection.executeAsync(this);
    }
}
