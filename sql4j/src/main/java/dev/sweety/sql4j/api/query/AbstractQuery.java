package dev.sweety.sql4j.api.query;

public abstract non-sealed class AbstractQuery<T>
        implements Query<T> {

    protected abstract String buildSql();

    @Override
    public final String sql() {
        String sql = buildSql();
        validateSql(sql);
        return sql;
    }

    protected void validateSql(final String sql) {
        if (sql.contains(";")) throw new IllegalStateException("Multiple statements not allowed");
    }
}
