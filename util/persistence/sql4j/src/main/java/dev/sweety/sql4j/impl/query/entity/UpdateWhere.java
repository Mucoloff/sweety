package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jQueryException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.ConditionalUpdateQuery;

import dev.sweety.sql4j.impl.cache.EntityCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UpdateWhere<T> extends AbstractQuery<Integer> implements ConditionalUpdateQuery<T> {

    private final Table<T> table;
    private final Map<Column<?>, Object> values;
    private final Criterion criterion;
    private final Dialect dialect;
    private final EntityCache entityCache;

    public UpdateWhere(Table<T> table, Dialect dialect, EntityCache entityCache) {
        this(table, dialect, Collections.emptyMap(), null, entityCache);
    }

    private UpdateWhere(Table<T> table, Dialect dialect, Map<Column<?>, Object> values, Criterion criterion, EntityCache entityCache) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect is null");
        this.values = new LinkedHashMap<>(values);
        this.criterion = criterion;
        this.entityCache = entityCache;
    }

    @Override
    public <V> UpdateWhere<T> set(Column<V> column, V value) {
        Map<Column<?>, Object> newValues = new LinkedHashMap<>(this.values);
        newValues.put(column, value);
        return new UpdateWhere<>(table, dialect, newValues, criterion, entityCache);
    }

    @Override
    public UpdateWhere<T> where(Criterion criterion) {
        return new UpdateWhere<>(table, dialect, values, criterion, entityCache);
    }

    @Override
    protected String buildSql() {
        if (values.isEmpty()) {
            throw new Sql4jQueryException("No values to update. Call set() at least once before executing an UPDATE WHERE query.");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(table.toSql(dialect)).append(" SET ");
        
        sql.append(values.keySet().stream()
                .map(c -> c.toSql(dialect) + " = ?")
                .collect(Collectors.joining(", ")));

        if (criterion != null) {
            sql.append(" WHERE ").append(criterion.toSql(dialect));
        }

        return sql.toString();
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        for (Object value : values.values()) {
            ps.setObject(idx++, value);
        }
        if (criterion != null) {
            criterion.bind(ps, idx);
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
