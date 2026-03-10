package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public final class DeleteEntity<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private final T[] instances;

    private final List<Column> primaryKeys;
    private final String sql;

    @SafeVarargs
    public DeleteEntity(final Table<T> table, final T... instances) {
        if (instances == null || instances.length == 0)
            throw new IllegalArgumentException("At least one instance is required");
        this.table = table;
        this.instances = instances;
        this.primaryKeys = table.primaryKeys();
        this.sql = buildSqlInternal();
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    private String buildSqlInternal() {
        StringBuilder sb = new StringBuilder("DELETE FROM ").append(table.name()).append(" WHERE ");
        if (primaryKeys.size() == 1) {
            sb.append(primaryKeys.getFirst().name()).append(" IN (");
            sb.append("?, ".repeat(instances.length));
            sb.setLength(sb.length() - 2);
            sb.append(")");
        } else {
            sb.append("(");
            sb.append(primaryKeys.stream().map(Column::name).collect(Collectors.joining(", ")));
            sb.append(") IN (");
            sb.append(
                    instances.length > 0
                            ? String.join(", ",
                            java.util.Collections.nCopies(instances.length,
                                    "(" + "?, ".repeat(primaryKeys.size()).replaceAll(", $", "") + ")"))
                            : ""
            );
            sb.append(")");
        }
        return sb.toString();
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        int idx = 1;
        for (T instance : instances) {
            for (Column pk : primaryKeys) {
                ps.setObject(idx++, pk.get(instance));
            }
        }
    }

    @Override
    public Integer execute(final PreparedStatement ps) throws SQLException {
        return ps.executeUpdate();
    }
}
