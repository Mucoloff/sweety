package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.QueryExecutor;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DependentQueryChain<I, O>
        implements QueryChain<O> {

    private final ChainableQuery<I, O> step;
    private final DependentQueryChain<?, I> previous;

    private DependentQueryChain(
            final DependentQueryChain<?, I> previous,
            final ChainableQuery<I, O> step
    ) {
        this.previous = previous;
        this.step = step;
    }

    private static final class Start<O>
            extends DependentQueryChain<Void, O> {

        private Start(final ChainableQuery<Void, O> step) {
            super(null, step);
        }
    }

    public static <O> DependentQueryChain<Void, O> start(final Query<O> first) {
        return new Start<>(v -> first);
    }

    public final <N> DependentQueryChain<O, N> then(final ChainableQuery<O, N> next) {
        return new DependentQueryChain<>(this, next);
    }

    public final CompletableFuture<O> execute(final SqlConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (final Connection con = connection.connection()) {
                return execute(con);
            } catch (final SQLException e) {
                throw new CompletionException(e);
            }
        }, SqlConnection.executor(connection.dialectType()));
    }

    @Override
    public final O execute(final Connection con) throws SQLException {
        final I input = previous == null ? null : previous.execute(con);
        final Query<O> q = step.build(input);
        return QueryExecutor.execute(con, q);
    }
}


