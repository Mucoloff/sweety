package dev.sweety.sql4j.impl.query.param;

import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;

import java.sql.PreparedStatement;
import java.sql.SQLException;

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

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private String sql;
        private QueryBinder binder = ps -> {};
        private QueryExecutor<T> executor;
        private boolean returnGeneratedKeys;

        public Builder<T> sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder<T> bind(QueryBinder binder) {
            this.binder = binder;
            return this;
        }

        public Builder<T> execute(QueryExecutor<T> executor) {
            this.executor = executor;
            return this;
        }

        public Builder<T> returnGeneratedKeys() {
            this.returnGeneratedKeys = true;
            return this;
        }

        public ParamQuery<T> build() {
            if (sql == null || executor == null)
                throw new IllegalStateException("sql and executor required");
            return new ParamQuery<>(this);
        }
    }

    @Override protected String buildSql() { return sql; }
    @Override public void bind(PreparedStatement ps) throws SQLException { binder.bind(ps); }
    @Override public T execute(PreparedStatement ps) throws SQLException { return executor.execute(ps); }
    @Override public boolean returnGeneratedKeys() { return returnGeneratedKeys; }
}

