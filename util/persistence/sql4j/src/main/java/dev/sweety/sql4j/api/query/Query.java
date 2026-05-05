package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.query.SelectJoin;
import dev.sweety.sql4j.impl.query.param.ParamQuery;
import dev.sweety.sql4j.impl.query.param.QueryResult;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public sealed interface Query<T> permits AbstractQuery, UnsafeQuery, SelectQuery, DeleteQuery, UpdateQuery, InsertQuery, UpsertQuery, SelectRawQuery, BatchQuery {

    void bind(final PreparedStatement ps) throws SQLException;

    T execute(final PreparedStatement ps) throws SQLException;

    String sql();

    default boolean returnGeneratedKeys() {
        return false;
    }

    default CompletableFuture<T> execute(final SqlConnection connection) {
        return connection.executeAsync(this);
    }

    default <R> Query<R> extractObjects(java.util.function.Function<T, R> mapper) {
        return new AbstractQuery<R>() {
            @Override protected String buildSql() { return Query.this.sql(); }
            @Override public void bind(PreparedStatement ps) throws SQLException { Query.this.bind(ps); }
            @Override public R execute(PreparedStatement ps) throws SQLException {
                return mapper.apply(Query.this.execute(ps));
            }
        };
    }

    // --- Utility Factory Methods ---

    static <T> Query<T> generate(final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return ParamQuery.<T>builder(query, execute).bind(bind).build();
    }

    static <T> CompletableFuture<T> execute(final SqlConnection connection, final String query,
                                             final QueryBinder bind, final QueryExecutor<T> execute) {
        return generate(query, bind, execute).execute(connection);
    }

    static Query<QueryResult> generic(final String query, final Object... params) {
        return generate(query, ps -> {
            if (params != null) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            }
        }, QueryResult::fromStatement);
    }

    static CompletableFuture<QueryResult> execute(final SqlConnection connection, final String query,
                                                   final Object... params) {
        return generic(query, params).execute(connection);
    }

    static SelectJoin.Builder join(Table<?>... tables) {
        return new SelectJoin.Builder().join(tables);
    }
}
