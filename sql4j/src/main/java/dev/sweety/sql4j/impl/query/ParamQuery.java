package dev.sweety.sql4j.impl.query;

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

    public ParamQuery(String sql,
                      QueryBinder binder,
                      QueryExecutor<T> executor) {
        this(sql, binder, executor, false);
    }

    public ParamQuery(String sql,
                      QueryBinder binder,
                      QueryExecutor<T> executor, boolean returnGeneratedKeys) {
        this.sql = sql;
        this.binder = binder;
        this.executor = executor;
        this.returnGeneratedKeys = returnGeneratedKeys;
    }

    @Override
    protected String buildSql() { return sql; }

    @Override
    public void bind(PreparedStatement ps) throws SQLException { binder.bind(ps); }

    @Override
    public T execute(PreparedStatement ps) throws SQLException { return executor.execute(ps); }

    @Override
    public boolean returnGeneratedKeys() {
        return returnGeneratedKeys;
    }
}
