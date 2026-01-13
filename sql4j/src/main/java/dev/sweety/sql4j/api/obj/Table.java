package dev.sweety.sql4j.api.obj;

import it.unimi.dsi.fastutil.Pair;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Table<T> {
    private final Class<T> clazz;
    private final String name;

    private final Map<String, Column> columnsMap;
    private final List<Column> columnsList;

    private Column primaryKey;

    public Table(Class<T> clazz, String name) {
        this.clazz = clazz;
        this.name = name;
        final int length = clazz.getDeclaredFields().length;
        this.columnsMap = new LinkedHashMap<>(length);
        this.columnsList = new ArrayList<>(length);
    }

    public void addColumn(Column column) {
        columnsMap.put(column.name(), column);
        columnsMap.put(name + "." + column.name(), column);
        columnsList.add(column);
    }

    public void immutable() {
        // No-op for now, but could be used to prevent further modifications
    }

    public String name() {
        return name;
    }

    public List<Column> columns() {
        return columnsList;
    }

    public Column getColumn(String name) {
        return columnsMap.get(name);
    }

    public boolean hasColumn(String columnName) {
        return columnsMap.containsKey(columnName);
    }

    public Class<T> clazz() {
        return clazz;
    }

    public Column primaryKey() {
        if (primaryKey != null) return primaryKey;
        Column pk = null;
        for (Column c : columnsList) {
            if (!c.isPrimaryKey()) continue;
            if (pk != null) throw new IllegalStateException("Multiple primary keys not supported");
            pk = c;
        }
        if (pk == null) throw new IllegalStateException("Primary key required for table " + name);
        return primaryKey = pk;
    }

    public Pair<List<Column>, Column> insertableColumns() {
        Column auto = null;
        List<Column> cols = new ArrayList<>(columnsList.size());
        for (Column c : columnsList) {
            if (c.isAutoIncrement()) {
                if (auto != null)
                    throw new IllegalStateException("Multiple autoIncrement columns not supported");
                auto = c;
                continue;
            }
            cols.add(c);
        }
        return Pair.of(cols, auto);
    }

    public List<Column> updatableColumns() {
        List<Column> cols = new ArrayList<>(columnsList.size());
        for (Column c : columnsList) {
            if (!c.isPrimaryKey()) cols.add(c);
        }
        return cols;
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
