package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public final class UpdateEntity<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private final T instance;
    private final Metadata metadata;

    private record Metadata(List<Column> updateColumns, List<Column> primaryKeys, String sql) {}

    public UpdateEntity(final Table<T> table, final T instance) {
        this.table = table;
        this.instance = instance;

        String cacheKey = "update:meta:" + table.name() + ":" + table.clazz().getName();
        this.metadata = QueryCache.getMetadata(cacheKey, _ -> {
            List<Column> primaryKeys = table.primaryKeys();
            List<Column> updateColumns = table.updatableColumns();

            String setClause = updateColumns.stream()
                    .map(Column::name)
                    .map(n -> n + "=?")
                    .collect(Collectors.joining(", "));

            String whereClause = primaryKeys.stream()
                    .map(Column::name)
                    .map(n -> n + "=?")
                    .collect(Collectors.joining(" AND "));

            String sql = "UPDATE " + table.name() + " SET " + setClause + " WHERE " + whereClause;
            return new Metadata(updateColumns, primaryKeys, sql);
        });
    }

    private UpdateEntity(Table<T> table, Metadata metadata, T instance) {
        this.table = table;
        this.metadata = metadata;
        this.instance = instance;
    }

    public UpdateEntity<T> copy(T instance) {
        return new UpdateEntity<>(table, metadata, instance);
    }

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        int idx = table.bindColumns(ps, metadata.updateColumns, instance, 1);
        for (Column pk : metadata.primaryKeys) ps.setObject(idx++, pk.get(instance));
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }
}
