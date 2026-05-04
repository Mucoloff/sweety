package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A chain of queries where each step's input is the output of the previous step.
 *
 * <p>Useful for dependent operations, e.g. insert a parent entity and use its generated key
 * to insert a child entity.
 *
 * <p>When invoked via {@link #execute(dev.sweety.sql4j.api.connection.SqlConnection)},
 * all steps run inside a single transaction — if any step fails the whole chain is rolled back.
 *
 * <p>Example:
 * <pre>{@code
 * db.transaction(
 *     DependentQueryChain.start(users.insert(user))
 *         .then(result -> orders.insert(new Order(result.value().getId())))
 * ).join();
 * }</pre>
 *
 * @param <I> Input type (output of the previous step)
 * @param <O> Output type of this step
 */
public class DependentQueryChain<I, O> implements QueryChain<O> {

    private final ChainableQuery<I, O> step;
    private final DependentQueryChain<?, I> previous;

    private DependentQueryChain(
            final DependentQueryChain<?, I> previous,
            final ChainableQuery<I, O> step
    ) {
        this.previous = previous;
        this.step = step;
    }

    private static final class Start<O> extends DependentQueryChain<Void, O> {
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

    /**
     * Executes all steps in dependency order on the given connection.
     * No transaction management — the caller controls commit/rollback.
     */
    @Override
    public final O execute(final Connection con) throws SQLException {
        final I input = previous == null ? null : previous.execute(con);
        final Query<O> q = step.build(input);
        return SqlRunner.execute(con, q);
    }
}
