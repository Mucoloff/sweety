package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.DeleteQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DeleteEntity<T> extends AbstractQuery<Integer> implements DeleteQuery<T> {

    private final Table<T> table;
    private final T[] instances;
    private final Metadata metadata;
    private final boolean hardDelete;

    private record Metadata(List<Column<?>> primaryKeys, String deleteSql, String softDeleteSql) {}

    public DeleteEntity(Table<T> table, Dialect dialect, QueryCache cache, T... instances) {
        this(table, dialect, cache, false, instances);
    }

    private DeleteEntity(Table<T> table, Dialect dialect, QueryCache cache, boolean hardDelete, T... instances) {
        this.table = Objects.requireNonNull(table, "table is null");
        this.instances = instances;
        this.hardDelete = hardDelete;
        Objects.requireNonNull(cache, "cache is null");

        String cacheKey = "delete:meta:" + table.name() + ":" + dialect.name();
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> primaryKeys = table.primaryKeys();
            String whereClause = primaryKeys.stream()
                    .map(c -> c.toSql(dialect) + "=?")
                    .collect(Collectors.joining(" AND "));

            String deleteSql = "DELETE FROM " + table.toSql(dialect) + " WHERE " + whereClause;
            
            String softDeleteSql = null;
            Column<?> sd = table.softDeleteColumn();
            if (sd != null) {
                softDeleteSql = "UPDATE " + table.toSql(dialect) + " SET " + sd.toSql(dialect) + " = " + dialect.softDeleteTrue() + " WHERE " + whereClause;
            }
            
            return new Metadata(primaryKeys, deleteSql, softDeleteSql);
        });
    }

    private DeleteEntity(Table<T> table, Metadata metadata, T[] instances, boolean hardDelete) {
        this.table = table;
        this.metadata = metadata;
        this.instances = instances;
        this.hardDelete = hardDelete;
    }

    public DeleteEntity<T> hardDelete() {
        return new DeleteEntity<>(table, metadata, instances, true);
    }

    public DeleteEntity<T> softDelete() {
        return new DeleteEntity<>(table, metadata, instances, false);
    }

    @Override
    protected String buildSql() {
        if (!hardDelete && metadata.softDeleteSql != null) {
            return metadata.softDeleteSql;
        }
        return metadata.deleteSql;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        if (instances != null && instances.length > 0) {
            int idx = 1;
            for (Column<?> pk : metadata.primaryKeys) {
                ps.setObject(idx++, pk.get(instances[0]));
            }
        }
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }

    public final DeleteEntity<T> copy(T... instances) {
        return new DeleteEntity<>(table, metadata, instances, hardDelete);
    }
}
