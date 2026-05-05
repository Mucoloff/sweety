package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public non-sealed interface BatchQuery<T> extends Query<int[]> {

    @Override
    default CompletableFuture<int[]> execute(final SqlConnection connection) {
        return connection.executeAsync(this);
    }
}
