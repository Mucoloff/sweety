package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.api.obj.InsertableColumns;
import dev.sweety.sql4j.api.query.MutationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.sweety.sql4j.api.query.UpsertQuery;

public final class UpsertEntity<T> extends AbstractQuery<MutationResult<T>> implements UpsertQuery<T> {

    private final Table<T> table;
    private final T instance;
    private final Metadata metadata;

    private record Metadata(List<Column<?>> insertColumns, @Nullable Column<?> generatedColumn, String sql) {}

    public UpsertEntity(Table<T> table, Dialect dialect, @NotNull T instance, QueryCache cache) {
        this.table = Objects.requireNonNull(table);
        this.instance = Objects.requireNonNull(instance);

        String cacheKey = "upsert:meta:" + table.name() + ":" + table.clazz().getName() + ":" + dialect.name();
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            InsertableColumns cols = table.insertableColumns();
            List<Column<?>> insertColumns = cols.columns();
            Column<?> generatedColumn = cols.autoIncrementColumn();

            List<String> insertColNames = insertColumns.stream().map(Column::name).collect(Collectors.toList());
            List<String> pkColNames = table.primaryKeys().stream().map(Column::name).collect(Collectors.toList());
            
            // By default, UPSERT updates all columns except the primary keys
            List<String> updateColNames = insertColumns.stream()
                    .map(Column::name)
                    .filter(name -> !pkColNames.contains(name))
                    .collect(Collectors.toList());

            if (pkColNames.isEmpty()) {
                throw new IllegalStateException("Table " + table.name() + " has no primary keys, cannot UPSERT.");
            }

            // We pass raw names to upsertSyntax, and let the dialect handle escaping inside it
            String sql = dialect.upsertSyntax(table.name(), insertColNames, updateColNames, pkColNames);

            return new Metadata(insertColumns, generatedColumn, sql);
        });
    }

    private UpsertEntity(Table<T> table, Metadata metadata, T instance) {
        this.table = table;
        this.metadata = metadata;
        this.instance = instance;
    }

    public UpsertEntity<T> copy(T instance) {
        return new UpsertEntity<>(table, metadata, instance);
    }

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        // In most dialects, UPSERT parameter bindings only require the INSERT parameters once.
        // H2 MERGE, SQLite/PostgreSQL ON CONFLICT, MySQL ON DUPLICATE KEY UPDATE all use the initial VALUES list.
        for (Column<?> c : metadata.insertColumns) c.set(ps, idx++, instance);
    }

    @Override
    public MutationResult<T> execute(PreparedStatement ps) throws SQLException {
        int affected = ps.executeUpdate();
        int generatedId = affected;

        if (metadata.generatedColumn != null) {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getInt(1);
                    metadata.generatedColumn.set(instance, generatedId);
                }
            } catch (SQLException ignore) {
                // Some drivers/dialects don't return generated keys on UPSERT when it updates instead of inserts
            }
        }
        return new MutationResult<>(generatedId, instance);
    }

    @Override
    public boolean returnGeneratedKeys() {
        return metadata.generatedColumn != null;
    }
}
