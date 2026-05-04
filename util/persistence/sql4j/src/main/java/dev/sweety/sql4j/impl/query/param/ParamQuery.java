package dev.sweety.sql4j.impl.query.param;

import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class ParamQuery<T> extends AbstractQuery<T> {

    private final String sql;
    private final QueryBinder binder;
    private final QueryExecutor<T> executor;
    private final boolean returnGeneratedKeys;

    private ParamQuery(Builder<T> b) {
        this.sql = b.sql;
        this.binder = b.binder;
        this.executor = b.executor;
        this.returnGeneratedKeys = b.returnGeneratedKeys;
    }

    // --- Typed builder (user specifies executor + return type) ---

    public static <T> Builder<T> builder(String sql, QueryExecutor<T> executor) {
        return new Builder<>(sql, executor);
    }

    /**
     * Convenience builder for SELECT queries that return {@code List<Row>}.
     * The executor is pre-configured to deserialize the ResultSet into {@link Row} objects.
     * Only {@link Builder#bind(QueryBinder)} and {@link Builder#build()} are needed.
     */
    public static Builder<List<Row>> rowBuilder(String sql) {
        return new Builder<>(sql, ps -> {
            try (ResultSet rs = ps.executeQuery()) {
                return Row.fromResultSetAll(rs);
            }
        });
    }

    public static final class Builder<T> {
        private final String sql;
        private final QueryExecutor<T> executor;
        private QueryBinder binder = QueryBinder.EMPTY;
        private boolean returnGeneratedKeys = false;

        Builder(String sql, QueryExecutor<T> executor) {
            this.sql = sql;
            this.executor = executor;
        }

        public Builder<T> bind(QueryBinder binder) {
            this.binder = binder;
            return this;
        }

        public Builder<T> returnGeneratedKeys() {
            this.returnGeneratedKeys = true;
            return this;
        }

        public ParamQuery<T> build() {
            if (sql == null || sql.isBlank()) throw new IllegalStateException("sql required");
            if (executor == null) throw new IllegalStateException("executor required");
            return new ParamQuery<>(this);
        }
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        binder.bind(ps);
    }

    @Override
    public T execute(PreparedStatement ps) throws SQLException {
        return executor.execute(ps);
    }

    @Override
    public boolean returnGeneratedKeys() {
        return returnGeneratedKeys;
    }
}
