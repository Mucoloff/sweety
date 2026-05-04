package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.query.Query;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A sequential chain of independent queries.
 *
 * <p>Each step is a {@link Supplier} that provides a {@link Query}. Steps are
 * executed in insertion order on the <em>same</em> connection. The result of the
 * last step is returned.
 *
 * <p>When invoked via {@link #execute(dev.sweety.sql4j.api.connection.SqlConnection)},
 * all steps run inside a single transaction — if any step fails the whole chain is rolled back.
 *
 * <p>Example:
 * <pre>{@code
 * db.transaction(
 *     SimpleQueryChain.start(() -> users.insert(user))
 *                     .then(() -> orders.insert(order))
 * ).join();
 * }</pre>
 */
public final class SimpleQueryChain<T> implements QueryChain<T> {

    private final List<Supplier<? extends Query<?>>> querySuppliers = new ArrayList<>();

    private SimpleQueryChain() {}

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

    /**
     * Executes all steps sequentially on the given connection.
     * No transaction management — the caller controls commit/rollback.
     */
    @Override
    public T execute(final Connection con) throws SQLException {
        T result = null;
        for (final Supplier<? extends Query<?>> supplier : querySuppliers) {
            //noinspection unchecked
            result = (T) SqlRunner.execute(con, supplier.get());
        }
        return result;
    }
}
