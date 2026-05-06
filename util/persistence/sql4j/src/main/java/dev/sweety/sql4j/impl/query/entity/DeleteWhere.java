package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import dev.sweety.sql4j.api.query.ConditionalDeleteQuery;

public final class DeleteWhere<T> extends AbstractQuery<Integer> implements ConditionalDeleteQuery<T> {

    private final Table<T> table;
    private final dev.sweety.sql4j.api.connection.dialect.Dialect dialect;
    private Criterion criterion;
    private boolean hardDelete = false;

    public DeleteWhere(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect is null");
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
    public DeleteWhere<T> softDelete() {
        this.hardDelete = false;
        return this;
    }

    @Override
    protected String buildSql() {
        Column<?> softDeleteCol = table.softDeleteColumn();
        boolean useSoftDelete = softDeleteCol != null && !hardDelete;

        StringBuilder sql = new StringBuilder();
        if (useSoftDelete) {
            sql.append("UPDATE ").append(table.toSql(dialect)).append(" SET ").append(softDeleteCol.toSql(dialect)).append(" = 1");
        } else {
            sql.append("DELETE FROM ").append(table.toSql(dialect));
        }

        if (criterion != null) {
            sql.append(" WHERE ").append(criterion.toSql(dialect));
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
