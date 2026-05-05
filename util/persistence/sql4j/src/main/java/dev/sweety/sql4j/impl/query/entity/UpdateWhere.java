package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.sweety.sql4j.api.query.ConditionalUpdateQuery;

public final class UpdateWhere<T> extends AbstractQuery<Integer> implements ConditionalUpdateQuery<T> {

    private final Table<T> table;
    private final Map<Column<?>, Object> values = new LinkedHashMap<>();
    private Criterion criterion;

    public UpdateWhere(Table<T> table) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
    }

    public <V> UpdateWhere<T> set(Column<V> column, V value) {
        values.put(column, value);
        return this;
    }

    public UpdateWhere<T> where(Criterion criterion) {
        this.criterion = criterion;
        return this;
    }

    @Override
    protected String buildSql() {
        if (values.isEmpty()) {
            throw new IllegalStateException("No values to update. Call set() at least once.");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(table.name()).append(" SET ");
        
        sql.append(values.keySet().stream()
                .map(c -> c.name() + " = ?")
                .collect(Collectors.joining(", ")));

        if (criterion != null) {
            sql.append(" WHERE ").append(criterion.toSql());
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
        return ps.executeUpdate();
    }
}
