package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public final class DeleteEntity<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private final T[] instances;
    private final Metadata metadata;

    private record Metadata(List<Column> primaryKeys, String sql) {}

    @SafeVarargs
    public DeleteEntity(final Table<T> table, final T... instances) {
        this.table = table;
        this.instances = instances;

        // Note: delete prototype is only valid for a specific number of instances
        int instancesCount = instances != null ? instances.length : 0;

        String cacheKey = "delete:meta:" + table.name() + ":" + table.clazz().getName() + ":" + instancesCount;
        this.metadata = QueryCache.getMetadata(cacheKey, _ -> {
            List<Column> pks = table.primaryKeys();
            StringBuilder sb = new StringBuilder("DELETE FROM ").append(table.name()).append(" WHERE ");
            if (pks.size() == 1) {
                sb.append(pks.getFirst().name()).append(" IN (");
                sb.repeat("?, ", instancesCount);
                if (instancesCount > 0) sb.setLength(sb.length() - 2);
                sb.append(")");
            } else {
                sb.append("(");
                sb.append(pks.stream().map(Column::name).collect(Collectors.joining(", ")));
                sb.append(") IN (");
                sb.append(
                        instancesCount > 0
                                ? String.join(", ",
                                java.util.Collections.nCopies(instancesCount,
                                        "(" + "?, ".repeat(pks.size()).replaceAll(", $", "") + ")"))
                                : ""
                );
                sb.append(")");
            }
            return new Metadata(pks, sb.toString());
        });
    }

    private DeleteEntity(Table<T> table, Metadata metadata, T[] instances) {
        this.table = table;
        this.metadata = metadata;
        this.instances = instances;
    }

    @SafeVarargs
    public final DeleteEntity<T> copy(T... instances) {
        return new DeleteEntity<>(table, metadata, instances);
    }

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        if (instances == null) return;
        int idx = 1;
        for (T instance : instances) {
            for (Column pk : metadata.primaryKeys) {
                ps.setObject(idx++, pk.get(instance));
            }
        }
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }
}
