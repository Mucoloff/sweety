package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public final class UpdateEntity<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private final T instance;

    private final List<Column> updateColumns;
    private final List<Column> primaryKeys;
    private final String sql;

    public UpdateEntity(final Table<T> table, final T instance) {
        this.table = table;
        this.instance = instance;

        this.primaryKeys = table.primaryKeys();
        this.updateColumns = table.updatableColumns();
        this.sql = buildSqlInternal();
    }

    @Override
    protected String buildSql() {
        return sql;
    }


    private String buildSqlInternal() {
        String setClause = updateColumns.stream()
                .map(Column::name)
                .map(n -> n + "=?")
                .collect(Collectors.joining(", "));

        String whereClause = primaryKeys.stream()
                .map(Column::name)
                .map(n -> n + "=?")
                .collect(Collectors.joining(" AND "));

        return "UPDATE " + table.name() + " SET " + setClause + " WHERE " + whereClause;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        int idx = table.bindColumns(ps, updateColumns, instance, 1);
        for (Column pk : primaryKeys) ps.setObject(idx++, pk.get(instance));
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }

}
