package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.BatchQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UpdateBatch<T> extends AbstractQuery<int[]> implements BatchQuery<T> {

    private final Table<T> table;
    private final Collection<T> instances;
    private final Metadata metadata;

    private record Metadata(List<Column<?>> updateColumns, List<Column<?>> primaryKeys, String sql) {}

    public UpdateBatch(Table<T> table, Collection<T> instances, QueryCache cache) {
        this.table = Objects.requireNonNull(table, "table is null");
        this.instances = Objects.requireNonNull(instances, "instances is null");
        Objects.requireNonNull(cache, "cache is null");

        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Cannot update an empty batch");
        }

        String cacheKey = "updateBatch:meta:" + table.name() + ":" + table.clazz().getName();
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> primaryKeys = table.primaryKeys();
            List<Column<?>> updateColumns = table.updatableColumns();

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

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        for (T instance : instances) {
            int i = 1;
            for (Column c : metadata.updateColumns) {
                ps.setObject(i++, c.get(instance));
            }
            for (Column c : metadata.primaryKeys) {
                ps.setObject(i++, c.get(instance));
            }
            ps.addBatch();
        }
    }

    @Override
    public int[] execute(PreparedStatement ps) throws SQLException {
        return ps.executeBatch();
    }
}
