package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public interface QueryChain<T> {

    CompletableFuture<T> execute(final SqlConnection connection);
    T execute(final Connection con) throws SQLException;
}
