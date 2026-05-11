package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.exception.Sql4jQueryException;

public abstract non-sealed class AbstractQuery<T>
        implements Query<T> {

    protected abstract String buildSql();

    @Override
    public final String sql() {
        String sql = buildSql();
        validateSql(sql);
        return sql;
    }

    /**
     * Validates the generated SQL before execution.
     * PreparedStatements already prevent injection — this is a sanity check only.
     */
    protected void validateSql(final String sql) {
        if (sql == null || sql.isBlank())
            throw new Sql4jQueryException("SQL query cannot be null or blank");
    }
}
