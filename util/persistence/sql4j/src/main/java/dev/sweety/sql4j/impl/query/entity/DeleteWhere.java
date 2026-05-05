package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class DeleteWhere<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private Criterion criterion;
    private boolean hardDelete = false;

    public DeleteWhere(Table<T> table) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
    }

    public DeleteWhere<T> where(Criterion criterion) {
        this.criterion = criterion;
        return this;
    }

    public DeleteWhere<T> hardDelete() {
        this.hardDelete = true;
        return this;
    }

    @Override
    protected String buildSql() {
        Column<?> softDeleteCol = table.softDeleteColumn();
        boolean useSoftDelete = softDeleteCol != null && !hardDelete;

        StringBuilder sql = new StringBuilder();
        if (useSoftDelete) {
            sql.append("UPDATE ").append(table.name()).append(" SET ").append(softDeleteCol.name()).append(" = 1");
        } else {
            sql.append("DELETE FROM ").append(table.name());
        }

        if (criterion != null) {
            sql.append(" WHERE ").append(criterion.toSql());
        }
        
        return sql.toString();
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        if (criterion != null) {
            criterion.bind(ps, 1);
        }
    }

    @Override
    public Integer execute(PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }
}
