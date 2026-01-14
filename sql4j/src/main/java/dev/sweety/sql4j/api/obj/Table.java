package dev.sweety.sql4j.api.obj;

import dev.sweety.sql4j.api.obj.table.TableRegistry;
import it.unimi.dsi.fastutil.Pair;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Table<T> {
    private final Class<T> clazz;
    private final String name;

    private final Map<String, Column> columnsMap = new LinkedHashMap<>();
    private final List<Column> columnsList = new ArrayList<>();
    private final List<Column> primaryKeys = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();

    public Table(Class<T> clazz, String name) {
        this.clazz = clazz;
        this.name = name;

        for (final Field field : clazz.getDeclaredFields()) {
            final Column.Info columnInfo = field.getAnnotation(Column.Info.class);
            if (columnInfo == null) continue;

            final String columnName = !columnInfo.name().isEmpty() ? columnInfo.name() : field.getName();
            final Column column = new Column(columnName, field, columnInfo);
            this.addColumn(column);

            final ForeignKey.Info fk = field.getAnnotation(ForeignKey.Info.class);
            if (fk != null) {
                Table<?> refTable = TableRegistry.get(fk.table());
                Column refCol = refTable.getColumn(fk.column());

                this.addForeignKey(new ForeignKey(
                        column,
                        refTable,
                        refCol,
                        true,
                        fk.onDelete(),
                        fk.onUpdate()
                ));
            }
        }
    }

    public void addColumn(Column column) {
        columnsMap.put(column.name(), column);
        columnsMap.put(name + "." + column.name(), column);
        columnsList.add(column);
        if (column.isPrimaryKey()) primaryKeys.add(column);
    }

    public void addForeignKey(ForeignKey fk) {
        foreignKeys.add(fk);
    }

    public List<Column> columns() {
        return List.copyOf(columnsList);
    }

    public Column getColumn(String name) {
        return columnsMap.get(name);
    }

    public List<Column> primaryKeys() {
        if (primaryKeys.isEmpty())
            throw new IllegalStateException("Primary key required for table " + name);
        return List.copyOf(primaryKeys);
    }

    public List<ForeignKey> foreignKeys() {
        return List.copyOf(foreignKeys);
    }

    public String name() {
        return name;
    }

    public Class<T> clazz() {
        return clazz;
    }

    public List<Column> updatableColumns() {
        List<Column> cols = new ArrayList<>();
        for (Column c : columnsList) if (!c.isPrimaryKey()) cols.add(c);
        return cols;
    }

    public Pair<List<Column>, Column> insertableColumns() {
        Column auto = null;
        List<Column> cols = new ArrayList<>();
        for (Column c : columnsList) {
            if (c.isAutoIncrement()) {
                if (auto != null) throw new IllegalStateException("Multiple autoIncrement columns not supported");
                auto = c;
                continue;
            }
            cols.add(c);
        }
        return Pair.of(cols, auto);
    }

    public int bindColumns(PreparedStatement ps, List<Column> cols, Object instance, int startIdx) throws SQLException {
        int idx = startIdx;
        for (Column c : cols) c.set(ps, idx++, instance);
        return idx;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Info {
        String name();
    }
}
