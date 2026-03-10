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
import java.util.function.Supplier;

public final class SimpleQueryChain<T> implements QueryChain<T> {

    private final List<Supplier<? extends Query<?>>> querySuppliers = new ArrayList<>();

    public static <T> SimpleQueryChain<T> start(Supplier<? extends Query<T>> first) {
        SimpleQueryChain<T> chain = new SimpleQueryChain<>();
        chain.querySuppliers.add(first);
        return chain;
    }

    public <N> SimpleQueryChain<N> then(Supplier<? extends Query<N>> next) {
        querySuppliers.add(next);
        //noinspection unchecked
        return (SimpleQueryChain<N>) this;
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
        for (final Supplier<? extends Query<?>> q : querySuppliers) {
            //noinspection unchecked
            t = (T) QueryExecutor.execute(con, q.get());
        }
        return t;
    }

}
