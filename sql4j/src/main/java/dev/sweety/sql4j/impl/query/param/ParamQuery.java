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

    private ParamQuery(String sql, QueryBinder binder, QueryExecutor<T> executor, boolean returnGeneratedKeys) {
        this.sql = sql;
        this.binder = binder;
        this.executor = executor;
        this.returnGeneratedKeys = returnGeneratedKeys;
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static final class Builder<T> {
        private String sql;
        private QueryBinder binder;
        private QueryExecutor<T> executor;
        private boolean returnGeneratedKeys = false;

        public Builder<T> sql(String sql) { this.sql = sql; return this; }
        public Builder<T> binder(QueryBinder binder) { this.binder = binder; return this; }
        public Builder<T> executor(QueryExecutor<T> executor) { this.executor = executor; return this; }
        public Builder<T> returnGeneratedKeys(boolean b) { this.returnGeneratedKeys = b; return this; }

        public ParamQuery<T> build() {
            if (sql == null || binder == null || executor == null)
                throw new IllegalStateException("SQL, binder e executor devono essere specificati");
            return new ParamQuery<>(sql, binder, executor, returnGeneratedKeys);
        }
    }

    @Override
    protected String buildSql() { return sql; }

    @Override
    public void bind(PreparedStatement ps) throws SQLException { binder.bind(ps); }

    @Override
    public T execute(PreparedStatement ps) throws SQLException { return executor.execute(ps); }

    @Override
    public boolean returnGeneratedKeys() { return returnGeneratedKeys; }
}
