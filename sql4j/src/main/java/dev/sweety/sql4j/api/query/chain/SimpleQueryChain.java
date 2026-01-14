package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.QueryExecutor;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SimpleQueryChain<T> implements QueryChain<T> {

    private final List<Query<?>> queries = new ArrayList<>();

    private SimpleQueryChain(final Query<?> first) {
        queries.add(first);
    }

    public static <T> SimpleQueryChain<T> start(final Query<?> first) {
        return new SimpleQueryChain<>(first);
    }

    public SimpleQueryChain<T> then(final Query<?> next) {
        queries.add(next);
        return this;
    }

    public CompletableFuture<T> execute(final SqlConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (final Connection con = connection.connection()) {
                return execute(con);
            } catch (final SQLException e) {
                throw new CompletionException(e);
            }
        }, SqlConnection.executor(connection.dialectType()));
    }

    @Override
    public T execute(final Connection con) throws SQLException {
        T t = null;
        for (final Query<?> q : queries) {
            //noinspection unchecked
            t = (T) QueryExecutor.execute(con, q);
        }
        return t;
    }

}
