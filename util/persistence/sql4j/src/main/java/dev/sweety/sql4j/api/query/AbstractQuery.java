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

    /**
     * Validates the generated SQL before execution.
     * PreparedStatements already prevent injection — this is a sanity check only.
     */
    protected void validateSql(final String sql) {
        if (sql == null || sql.isBlank())
            throw new IllegalStateException("SQL query cannot be null or blank");
    }
}
