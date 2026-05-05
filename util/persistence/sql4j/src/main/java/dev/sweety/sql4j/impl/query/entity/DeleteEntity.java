package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.DeleteQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DeleteEntity<T> extends AbstractQuery<Integer> implements DeleteQuery<T> {

    private final Table<T> table;
    private final T[] instances;
    private final Metadata metadata;
    private boolean hardDelete = false;

    private record Metadata(List<Column<?>> primaryKeys, Column<?> softDeleteColumn, String softDeleteSql, String hardDeleteSql) {}

    @SafeVarargs
    public DeleteEntity(final Table<T> table, QueryCache cache, final T... instances) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.instances = Objects.requireNonNull(instances, "instances cannot be null");
        Objects.requireNonNull(cache, "cache cannot be null");

        int instancesCount = instances.length;
        Column<?> softDeleteCol = table.softDeleteColumn();

        String cacheKey = "delete:meta:" + table.name() + ":" + table.clazz().getName() + ":" + instancesCount;
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> pks = table.primaryKeys();
            if (pks.isEmpty()) {
                throw new IllegalStateException("Table " + table.name() + " must have a primary key for entity-based deletion");
            }
            
            String wherePart;
            if (instancesCount == 0) {
                wherePart = "1 = 0"; 
            } else if (pks.size() == 1) {
                StringBuilder w = new StringBuilder();
                w.append(pks.getFirst().name()).append(" IN (");
                w.repeat("?, ", instancesCount);
                w.setLength(w.length() - 2);
                w.append(")");
                wherePart = w.toString();
            } else {
                wherePart = "(" +
                        pks.stream().map(Column::name).collect(Collectors.joining(", ")) +
                        ") IN (" +
                        String.join(", ",
                                Collections.nCopies(instancesCount,
                                        "(" + "?, ".repeat(pks.size()).replaceAll(", $", "") + ")")) +
                        ")";
            }

            String hardDeleteSql = "DELETE FROM " + table.name() + " WHERE " + wherePart;
            String softDeleteSql = softDeleteCol != null 
                    ? "UPDATE " + table.name() + " SET " + softDeleteCol.name() + " = 1 WHERE " + wherePart
                    : hardDeleteSql;

            return new Metadata(pks, softDeleteCol, softDeleteSql, hardDeleteSql);
        });
    }

    public DeleteEntity<T> hardDelete() {
        DeleteEntity<T> copy = copy(instances);
        copy.hardDelete = true;
        return copy;
    }

    @Override
    public DeleteEntity<T> softDelete() {
        DeleteEntity<T> copy = copy(instances);
        copy.hardDelete = false;
        return copy;
    }

    private DeleteEntity(Table<T> table, Metadata metadata, T[] instances, boolean hardDelete) {
        this.table = table;
        this.metadata = metadata;
        this.instances = instances;
        this.hardDelete = hardDelete;
    }

    @SafeVarargs
    public final DeleteEntity<T> copy(T... instances) {
        return new DeleteEntity<>(table, metadata, instances, false);
    }

    @Override
    protected String buildSql() {
        return (metadata.softDeleteColumn != null && !hardDelete) ? metadata.softDeleteSql : metadata.hardDeleteSql;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        if (instances == null) return;
        int idx = 1;
        for (T instance : instances) {
            for (Column<?> pk : metadata.primaryKeys) {
                ps.setObject(idx++, pk.get(instance));
            }
        }
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        int rows = ps.executeUpdate();
        if (rows > 0 && metadata.softDeleteColumn != null && !hardDelete && instances != null) {
            for (T instance : instances) {
                try {
                    // Logic for soft delete value (could be boolean or int)
                    // We assume 1/true for deleted.
                    Class<?> type = metadata.softDeleteColumn.field().getType();
                    if (type == boolean.class || type == Boolean.class) {
                        metadata.softDeleteColumn.set(instance, true);
                    } else {
                        metadata.softDeleteColumn.set(instance, 1);
                    }
                } catch (Exception ignored) {
                    // Fail silently for in-memory update if something is wrong with the field
                }
            }
        }
        return rows;
    }
}
