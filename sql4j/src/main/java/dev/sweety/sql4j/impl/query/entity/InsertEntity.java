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
    private final List<Column> insertColumns;
    private final Column generatedColumn;
    private final int fieldsPerRow;
    private final String sql;

    public InsertEntity(Table<T> table, @NotNull T instance) {
        this.table = table;
        this.instance = instance;

        Pair<List<Column>, Column> cols = table.insertableColumns();
        this.insertColumns = cols.key();
        this.generatedColumn = cols.value();
        this.fieldsPerRow = insertColumns.size();
        this.sql = buildSqlInternal();
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    private String buildSqlInternal() {
        String cols = insertColumns.stream().map(Column::name).collect(Collectors.joining(", "));
        String placeholders = "(" + "?,".repeat(fieldsPerRow).replaceAll(",$", "") + ")";
        return "INSERT INTO " + table.name() + " (" + cols + ") VALUES " + placeholders;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        for (Column c : insertColumns) c.set(ps, idx++, instance);

    }

    @Override
    public Pair<Integer, T> execute(PreparedStatement ps) throws SQLException {
        int updated = ps.executeUpdate();

        if (generatedColumn != null) {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Object key = rs.getObject(1);
                    generatedColumn.set(instance, key);
                }
            }
        }
        return Pair.of(updated, instance);
    }

    @Override
    public boolean returnGeneratedKeys() {
        return generatedColumn != null;
    }
}
