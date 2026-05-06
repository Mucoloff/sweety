package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.ConditionalDeleteQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class DeleteWhere<T> extends AbstractQuery<Integer> implements ConditionalDeleteQuery<T> {

    private final Table<T> table;
    private final dev.sweety.sql4j.api.connection.dialect.Dialect dialect;
    private final Criterion criterion;
    private final boolean hardDelete;
    private final dev.sweety.sql4j.impl.cache.EntityCache entityCache;

    public DeleteWhere(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
        this(table, dialect, null, false, null);
    }

    public DeleteWhere(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect, dev.sweety.sql4j.impl.cache.EntityCache entityCache) {
        this(table, dialect, null, false, entityCache);
    }

    private DeleteWhere(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect, Criterion criterion, boolean hardDelete, dev.sweety.sql4j.impl.cache.EntityCache entityCache) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect is null");
        this.criterion = criterion;
        this.hardDelete = hardDelete;
        this.entityCache = entityCache;
    }

    @Override
    public DeleteWhere<T> where(Criterion criterion) {
        return new DeleteWhere<>(table, dialect, criterion, hardDelete, entityCache);
    }

    @Override
    public DeleteWhere<T> hardDelete() {
        return new DeleteWhere<>(table, dialect, criterion, true, entityCache);
    }

    @Override
    public DeleteWhere<T> softDelete() {
        return new DeleteWhere<>(table, dialect, criterion, false, entityCache);
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
        int rows = ps.executeUpdate();
        if (rows > 0 && entityCache != null) {
            entityCache.evictAll(table.clazz());
        }
        return rows;
    }
}
