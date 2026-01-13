package dev.sweety.sql4j.api.obj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
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

    public Column getColumn(String name) {
        return columnsMap.get(name);
    }

    public String name() {
        return name;
    }

    public List<Column> columns() {
        return columnsList;
    }

    public boolean hasColumn(String columnName) {
        return columnsMap.containsKey(columnName);
    }



    public Class<T> clazz() {
        return clazz;
    }


    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Info {
        String name();
    }
}
