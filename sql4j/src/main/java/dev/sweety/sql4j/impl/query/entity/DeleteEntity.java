package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class DeleteEntity<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private final T[] instances;

    private final String sql;

    @SafeVarargs
    public DeleteEntity(final Table<T> table, final T... instances) {
        if (instances == null || instances.length == 0)
            throw new IllegalArgumentException("At least one instance is required");
        this.table = table;
        this.instances = instances;
        // build sql once
        this.sql = buildSqlInternal();
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    private String buildSqlInternal() {
        final StringBuilder b = new StringBuilder();
        b.append("?, ".repeat(instances.length));
        b.setLength(b.length() - 2);
        return "DELETE FROM " + table.name() + " WHERE " + table.primaryKey().name() + " IN (" + b + ")";
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        for (int i = 0; i < instances.length; i++) {
            ps.setObject(i + 1, table.primaryKey().get(instances[i]));
        }
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }
}
