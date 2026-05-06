package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.BatchQuery;
import dev.sweety.sql4j.impl.query.QueryCache;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class InsertBatch<T> extends AbstractQuery<int[]> implements BatchQuery<T> {

    private final Table<T> table;
    private final Collection<T> instances;
    private final Metadata metadata;

    private record Metadata(List<Column<?>> insertColumns, @Nullable Column<?> generatedColumn, String sql) {}

    public InsertBatch(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect, Collection<T> instances, QueryCache cache) {
        this.table = Objects.requireNonNull(table, "table is null");
        this.instances = Objects.requireNonNull(instances, "instances is null");
        Objects.requireNonNull(cache, "cache is null");
        Objects.requireNonNull(table.insertableColumns(), "table.insertableColumns() is null for " + table.name());

        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Cannot insert an empty batch");
        }

        // Determine active columns based on the first instance.
        // For batch operations, it is assumed that all instances share the same schema structure.
        T firstInstance = instances.iterator().next();
        List<Column<?>> allInsertable = table.insertableColumns().columns();
        List<Column<?>> activeColumns = new java.util.ArrayList<>();
        for (Column<?> c : allInsertable) {
            Object val = c.get(firstInstance);
            if (val == null && c.defaultValue() != null && !c.defaultValue().isEmpty()) {
                continue; // Skip to let DB use default
            }
            activeColumns.add(c);
        }

        String colKey = activeColumns.stream().map(Column::name).sorted().collect(Collectors.joining(","));
        String cacheKey = "insertBatch:meta:" + table.name() + ":" + colKey + ":" + dialect.name();

        this.metadata = cache.getMetadata(cacheKey, _ -> {
            Column<?> generatedColumn = table.insertableColumns().autoIncrementColumn();
            int fieldsPerRow = activeColumns.size();

            String colNames = activeColumns.stream().map(c -> c.toSql(dialect)).collect(Collectors.joining(", "));
            String placeholders = "(" + "?,".repeat(fieldsPerRow).replaceAll(",$", "") + ")";
            String sql = "INSERT INTO " + table.toSql(dialect) + " (" + colNames + ") VALUES " + placeholders;

            return new Metadata(activeColumns, generatedColumn, sql);
        });
    }

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public boolean returnGeneratedKeys() {
        return false;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        // In Batch execution, binding is done per row and added to batch
        for (T instance : instances) {
            int i = 1;
            for (Column c : metadata.insertColumns) {
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
