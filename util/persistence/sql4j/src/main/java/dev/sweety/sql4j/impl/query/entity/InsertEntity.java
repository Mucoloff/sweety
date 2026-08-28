package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.api.obj.InsertableColumns;
import dev.sweety.sql4j.api.query.MutationResult;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.sweety.sql4j.api.query.InsertQuery;

public final class InsertEntity<T> extends AbstractQuery<MutationResult<T>> implements InsertQuery<T> {

    private final Table<T> table;
    private final T instance;
    private final Metadata metadata;

    private record Metadata(List<Column<?>> insertColumns, @Nullable Column<?> generatedColumn, String sql) {
    }

    public InsertEntity(Table<T> table, Dialect dialect, T instance, QueryCache cache) {
        this.table = Objects.requireNonNull(table, "table is null");
        this.instance = Objects.requireNonNull(instance, "instance is null");
        Objects.requireNonNull(cache, "cache is null");
        Objects.requireNonNull(table.insertableColumns(), "table.insertableColumns() is null for " + table.name());

        // Calculate which columns are present (non-null or no default)
        List<Column<?>> allInsertable = table.insertableColumns().columns();
        List<Column<?>> activeColumns = new ArrayList<>();
        for (Column<?> c : allInsertable) {
            Object val = c.get(instance);
            if (val == null && c.defaultValue() != null && !c.defaultValue().isEmpty()) {
                continue; // Skip to let DB use default
            }
            activeColumns.add(c);
        }

        String colKey = activeColumns.stream().map(Column::name).sorted().collect(Collectors.joining(","));
        String cacheKey = "insert:meta:" + table.name() + ":" + colKey + ":" + dialect.name();

        this.metadata = cache.getMetadata(cacheKey, ignored -> {
            Column<?> generatedColumn = table.insertableColumns().autoIncrementColumn();
            int fieldsPerRow = activeColumns.size();

            String colNames = activeColumns.stream().map(c -> c.toSql(dialect)).collect(Collectors.joining(", "));
            String placeholders = "(" + "?,".repeat(fieldsPerRow).replaceAll(",$", "") + ")";
            String sql = "INSERT INTO " + table.toSql(dialect) + " (" + colNames + ") VALUES " + placeholders;

            return new Metadata(activeColumns, generatedColumn, sql);
        });
    }

    private InsertEntity(Table<T> table, Metadata metadata, T instance) {
        this.table = table;
        this.metadata = metadata;
        this.instance = instance;
    }

    public InsertEntity<T> copy(T instance) {
        return new InsertEntity<>(table, metadata, instance);
    }

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        for (Column c : metadata.insertColumns) c.set(ps, idx++, instance);
    }

    @Override
    public MutationResult<T> execute(PreparedStatement ps) throws SQLException {
        int affected = ps.executeUpdate();

        if (metadata.generatedColumn != null) {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Object id = rs.getObject(1);
                    metadata.generatedColumn.set(instance, id);
                }
            }
        }
        return new MutationResult<>(affected, instance);
    }

    @Override
    public boolean returnGeneratedKeys() {
        return metadata.generatedColumn != null;
    }
}
