package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class InsertQuery<T> extends AbstractQuery<Integer> {

    private final Table<T> table;
    private final T[] instances;

    private final List<Column> insertColumns;
    private final Column generatedColumn;
    private final int fieldsPerRow;
    private final String sql;

    @SafeVarargs
    public InsertQuery(Table<T> table, T... instances) {
        if (instances == null || instances.length == 0)
            throw new IllegalArgumentException("At least one instance is required");

        this.table = table;
        this.instances = instances;

        List<Column> cols = new ArrayList<>();
        Column auto = null;

        for (Column c : table.columns()) {
            if (c.info().autoIncrement()) {
                if (auto != null)
                    throw new IllegalStateException("Multiple autoIncrement columns not supported");
                auto = c;
                continue;
            }
            cols.add(c);
        }

        if (instances.length > 1 && auto != null)
            throw new IllegalStateException(
                    "Batch insert with generated keys is not supported");


        this.insertColumns = List.copyOf(cols);
        this.generatedColumn = auto;
        this.fieldsPerRow = insertColumns.size();
        this.sql = buildSqlInternal();
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    private String buildSqlInternal() {
        String cols = insertColumns.stream()
                .map(Column::name)
                .collect(Collectors.joining(", "));

        String placeholders = "(" +
                "?,".repeat(fieldsPerRow).replaceAll(",$", "") +
                ")";

        String values = placeholders.repeat(instances.length)
                .replace(")(", "),(");

        return "INSERT INTO " + table.name() +
                " (" + cols + ") VALUES " + values;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        for (T instance : instances) {
            for (Column c : insertColumns) {
                c.set(ps, idx++, instance);
            }
        }
    }

    @Override
    public Integer execute(PreparedStatement ps) throws SQLException {
        int updated = ps.executeUpdate();

        if (generatedColumn != null) {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next() && i < instances.length) {
                    Object key = rs.getObject(1);
                    generatedColumn.set(instances[i++], key);
                }
            }
        }
        return updated;
    }

    @Override
    public boolean returnGeneratedKeys() {
        return generatedColumn != null;
    }
}
