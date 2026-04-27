package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.query.entity.DeleteEntity;
import dev.sweety.sql4j.impl.query.entity.InsertEntity;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import dev.sweety.sql4j.impl.query.entity.UpdateEntity;
import dev.sweety.sql4j.impl.query.param.ParamQuery;
import dev.sweety.sql4j.impl.query.param.QueryResult;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
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

    // --- Entity Factory Methods (with Prototype Recycling) ---

    static <T> InsertEntity<T> insert(Table<T> table, T instance) {
        return QueryCache.getQuery("insertPrototype:" + table.name(), _ -> new InsertEntity<>(table, null)).copy(instance);
    }

    static <T> UpdateEntity<T> update(Table<T> table, T instance) {
        return QueryCache.getQuery("updatePrototype:" + table.name(), _ -> new UpdateEntity<>(table, null)).copy(instance);
    }

    @SafeVarargs
    static <T> DeleteEntity<T> delete(Table<T> table, T... instances) {
        int count = instances != null ? instances.length : 0;
        return QueryCache.getQuery("deletePrototype:" + table.name() + ":" + count, _ -> new DeleteEntity<>(table, instances)).copy(instances);
    }

    static <T> SelectEntity<T> selectAll(Table<T> table) {
        return QueryCache.getQuery("selectAllPrototype:" + table.name(), _ -> new SelectEntity<>(table));
    }

    static <T> SelectEntity<T> selectWhere(Table<T> table, String where, Object... params) {
        return QueryCache.getQuery("selectWherePrototype:" + table.name() + ":" + where, _ -> new SelectEntity<>(table, where)).copy(params);
    }

    // --- Utility Factory Methods ---

    static <T> Query<T> generate(final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return ParamQuery.<T>builder().sql(query).bind(bind).execute(execute).build();
    }

    static <T> CompletableFuture<T> execute(final SqlConnection connection, final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return generate(query, bind, execute).execute(connection);
    }

    static Query<QueryResult> generic(final String query, final Object... params) {
        return generate(query, ps -> {
            if (params != null) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            }
        }, QueryResult::fromStatement);
    }

    static CompletableFuture<QueryResult> execute(final SqlConnection connection, final String query, final Object... params) {
        return generic(query, params).execute(connection);
    }
}
