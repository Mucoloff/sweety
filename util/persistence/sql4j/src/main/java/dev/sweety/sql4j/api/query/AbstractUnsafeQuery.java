package dev.sweety.sql4j.api.query;

public abstract non-sealed class AbstractUnsafeQuery<T> extends AbstractQuery<T>
        implements UnsafeQuery<T> {

    @Override
    protected void validateSql(final String sql) {

    }
}
