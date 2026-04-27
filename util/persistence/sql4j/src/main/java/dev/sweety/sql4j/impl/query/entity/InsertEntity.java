package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public final class InsertEntity<T> extends AbstractQuery<Pair<Integer, T>> {

    private final Table<T> table;
    private final T instance;
    private final Metadata metadata;

    private record Metadata(List<Column> insertColumns, Column generatedColumn, int fieldsPerRow, String sql) {}

    public InsertEntity(Table<T> table, T instance) {
        this.table = table;
        this.instance = instance;

        String cacheKey = "insert:meta:" + table.name() + ":" + table.clazz().getName();
        this.metadata = dev.sweety.sql4j.impl.query.QueryCache.getMetadata(cacheKey, _ -> {
            Pair<List<Column>, Column> cols = table.insertableColumns();
            List<Column> insertColumns = cols.key();
            Column generatedColumn = cols.value();
            int fieldsPerRow = insertColumns.size();

            String colNames = insertColumns.stream().map(Column::name).collect(Collectors.joining(", "));
            String placeholders = "(" + "?,".repeat(fieldsPerRow).replaceAll(",$", "") + ")";
            String sql = "INSERT INTO " + table.name() + " (" + colNames + ") VALUES " + placeholders;

            return new Metadata(insertColumns, generatedColumn, fieldsPerRow, sql);
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
    public Pair<Integer, T> execute(PreparedStatement ps) throws SQLException {
        int updated = ps.executeUpdate();

        if (metadata.generatedColumn != null) {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Object key = rs.getObject(1);
                    metadata.generatedColumn.set(instance, key);
                }
            }
        }
        return Pair.of(updated, instance);
    }

    @Override
    public boolean returnGeneratedKeys() {
        return metadata.generatedColumn != null;
    }
}
