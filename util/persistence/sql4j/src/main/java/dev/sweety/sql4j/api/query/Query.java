package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.query.SelectJoin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Root of the SQL4J query hierarchy.
 *
 * <p>A {@code Query<T>} encapsulates three concerns:
 * <ol>
 *   <li><b>SQL generation</b> — {@link #sql()} builds (once, cached) the SQL string.</li>
 *   <li><b>Parameter binding</b> — {@link #bind(PreparedStatement)} sets {@code ?} values.</li>
 *   <li><b>Result mapping</b> — {@link #execute(PreparedStatement)} extracts the typed result.</li>
 * </ol>
 *
 * <p>The async entry point for callers is {@link #execute(SqlConnection)}, which borrows a
 * JDBC connection from the pool, delegates to {@link #bind} and {@link #execute(PreparedStatement)},
 * and returns a {@link CompletableFuture} running on the connection's executor.
 *
 * <p>This interface is {@code sealed}; the known subtypes are:
 * {@link AbstractQuery}, {@link UnsafeQuery}, {@link SelectQuery}, {@link DeleteQuery},
 * {@link UpdateQuery}, {@link InsertQuery}, {@link UpsertQuery}, {@link SelectRawQuery},
 * and {@link BatchQuery}.
 *
 * @param <T> the result type produced when this query is executed
 */
public sealed interface Query<T> permits AbstractQuery, BatchQuery, DeleteQuery, InsertQuery, SelectQuery, SelectRawQuery, UnsafeQuery, UpdateQuery, UpsertQuery {

    /**
     * Binds this query's parameters to the given {@link PreparedStatement}.
     * Called by the framework immediately before {@link #execute(PreparedStatement)}.
     *
     * @param ps the statement whose {@code ?} placeholders are to be filled
     * @throws SQLException if any JDBC binding call fails
     */
    void bind(PreparedStatement ps) throws SQLException;

    /**
     * Executes the already-bound {@link PreparedStatement} and maps the outcome to {@code T}.
     *
     * <p>For SELECT queries this typically calls {@link PreparedStatement#executeQuery()} and
     * iterates the {@link java.sql.ResultSet}. For mutations it calls
     * {@link PreparedStatement#executeUpdate()}.
     *
     * @param ps the statement to execute (already bound via {@link #bind})
     * @return the typed result — never {@code null} for collection results (use empty list instead)
     * @throws SQLException if JDBC execution or result-set iteration fails
     */
    T execute(PreparedStatement ps) throws SQLException;

    /**
     * Returns the SQL string for this query, including any {@code ?} placeholders.
     * Implementations are expected to build the string once (lazily or eagerly) and cache it.
     *
     * @return the non-null, non-empty SQL string
     */
    String sql();

    /**
     * Whether {@link PreparedStatement#RETURN_GENERATED_KEYS} should be requested when
     * the JDBC statement is prepared. Defaults to {@code false}.
     *
     * @return {@code true} for INSERT queries that expect an auto-generated primary key
     */
    default boolean returnGeneratedKeys() {
        return false;
    }

    /**
     * Executes this query asynchronously on the given {@link SqlConnection}.
     *
     * <p>The default implementation delegates to {@link SqlConnection#executeAsync(Query)},
     * which acquires a connection from the pool, calls {@link #bind} and
     * {@link #execute(PreparedStatement)}, then returns the connection to the pool.
     *
     * @param connection the SQL4J connection (wraps a JDBC {@link java.sql.Connection} pool)
     * @return a {@link CompletableFuture} that completes with the result on the connection's executor
     */
    default CompletableFuture<T> execute(final SqlConnection connection) {
        return connection.executeAsync(this);
    }

    /**
     * Returns a new {@code Query<R>} that applies {@code mapper} to this query's result.
     * The underlying SQL and parameter binding are unchanged.
     *
     * @param <R>    the transformed result type
     * @param mapper the mapping function applied to {@code T} after execution
     * @return a wrapped query whose execution returns {@code R}
     */
    default <R> Query<R> extractObjects(Function<T, R> mapper) {
        return new AbstractQuery<R>() {
            @Override
            protected String buildSql() {
                return Query.this.sql();
            }

            @Override
            public void bind(PreparedStatement ps) throws SQLException {
                Query.this.bind(ps);
            }

            @Override
            public R execute(PreparedStatement ps) throws SQLException {
                return mapper.apply(Query.this.execute(ps));
            }
        };
    }

    // ─── Static factory utilities ────────────────────────────────────────────────

    /**
     * Creates an ad-hoc query from raw SQL, a binder, and an executor.
     *
     * @param <T>     result type
     * @param query   the SQL string (with {@code ?} placeholders)
     * @param bind    sets parameters on the {@link PreparedStatement}
     * @param execute maps the executed statement to {@code T}
     * @return a new {@code Query<T>}
     */
    static <T> Query<T> generate(final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return ParamQuery.builder(query, execute).bind(bind).build();
    }

    /**
     * Shortcut that creates, executes, and returns the future of an ad-hoc query in one call.
     *
     * @param <T>        result type
     * @param connection the SQL4J connection
     * @param query      the SQL string
     * @param bind       parameter binder
     * @param execute    result mapper
     * @return a {@link CompletableFuture} completing with the query result
     */
    static <T> CompletableFuture<T> execute(final SqlConnection connection, final String query,
                                            final QueryBinder bind, final QueryExecutor<T> execute) {
        return generate(query, bind, execute).execute(connection);
    }

    /**
     * Creates a generic mutation or DDL query with positional {@code Object} parameters.
     * The result is a {@link QueryResult} that captures affected-row counts and generated keys.
     *
     * @param query  the SQL string
     * @param params positional parameter values (bound via {@link PreparedStatement#setObject})
     * @return a {@code Query<QueryResult>}
     */
    static Query<QueryResult> generic(final String query, final Object... params) {
        return generate(query, ps -> {
            if (params != null) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            }
        }, QueryResult::fromStatement);
    }

    /**
     * Executes a generic mutation/DDL query asynchronously.
     *
     * @param connection the SQL4J connection
     * @param query      the SQL string
     * @param params     positional parameter values
     * @return a {@link CompletableFuture} completing with {@link QueryResult}
     */
    static CompletableFuture<QueryResult> execute(final SqlConnection connection, final String query,
                                                  final Object... params) {
        return generic(query, params).execute(connection);
    }

    /**
     * Starts building a multi-table JOIN query.
     *
     * @param tables one or more table descriptors to join
     * @return a {@link SelectJoin.Builder} for configuring join conditions and columns
     */
    static SelectJoin.Builder join(Table<?>... tables) {
        return new SelectJoin.Builder().join(tables);
    }

    /**
     * Annotation for custom SQL queries in repository interfaces.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Info {
        /**
         * @return The raw SQL query. Use ? for parameters.
         */
        String value();

        /**
         * @return Whether this query is a native/raw query that should bypass DSL parsing.
         */
        boolean nativeQuery() default true;
    }

}
