package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DeleteEntity<T> extends AbstractQuery<Integer> {

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

        int instancesCount = instances != null ? instances.length : 0;
        Column<?> softDeleteCol = table.softDeleteColumn();

        String cacheKey = "delete:meta:" + table.name() + ":" + table.clazz().getName() + ":" + instancesCount;
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> pks = table.primaryKeys();
            
            String wherePart;
            if (pks.size() == 1) {
                StringBuilder w = new StringBuilder();
                w.append(pks.getFirst().name()).append(" IN (");
                w.repeat("?, ", instancesCount);
                if (instancesCount > 0) w.setLength(w.length() - 2);
                w.append(")");
                wherePart = w.toString();
            } else {
                StringBuilder w = new StringBuilder();
                w.append("(");
                w.append(pks.stream().map(Column::name).collect(Collectors.joining(", ")));
                w.append(") IN (");
                w.append(
                        instancesCount > 0
                                ? String.join(", ",
                                Collections.nCopies(instancesCount,
                                        "(" + "?, ".repeat(pks.size()).replaceAll(", $", "") + ")"))
                                : ""
                );
                w.append(")");
                wherePart = w.toString();
            }

            String hardDeleteSql = "DELETE FROM " + table.name() + " WHERE " + wherePart;
            String softDeleteSql = softDeleteCol != null 
                    ? "UPDATE " + table.name() + " SET " + softDeleteCol.name() + " = 1 WHERE " + wherePart
                    : hardDeleteSql;

            return new Metadata(pks, softDeleteCol, softDeleteSql, hardDeleteSql);
        });
    }

    public DeleteEntity<T> hardDelete() {
        this.hardDelete = true;
        return this;
    }

    private DeleteEntity(Table<T> table, Metadata metadata, T[] instances, boolean hardDelete) {
        this.table = table;
        this.metadata = metadata;
        this.instances = instances;
        this.hardDelete = hardDelete;
    }

    @SafeVarargs
    public final DeleteEntity<T> copy(T... instances) {
        return new DeleteEntity<>(table, metadata, instances, hardDelete);
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
        return ps.executeUpdate();
    }
}
