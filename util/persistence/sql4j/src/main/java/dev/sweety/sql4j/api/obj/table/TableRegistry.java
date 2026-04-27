package dev.sweety.sql4j.api.obj.table;

import dev.sweety.sql4j.api.obj.Table;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TableRegistry {

    private final Map<Class<?>, Table<?>> tableMap = new IdentityHashMap<>();

    public void register(Table<?> table) {
        synchronized (tableMap) {
            tableMap.put(table.clazz(), table);
        }
    }

    public <T> Table<T> get(Class<T> clazz) {
        synchronized (tableMap) {
            //noinspection unchecked
            Table<T> table = (Table<T>) tableMap.get(clazz);
            if (table == null) {
                Table.Info info = clazz.getAnnotation(Table.Info.class);
                if (info != null) {
                    table = new Table<>(clazz, info.name());
                    tableMap.put(clazz, table);
                    table.initialize(this);
                }
            }
            return table;
        }
    }

    public <T> Table<T> getOrCreate(Class<T> clazz, String customName) {
        synchronized (tableMap) {
            //noinspection unchecked
            Table<T> table = (Table<T>) tableMap.get(clazz);
            if (table == null || !table.name().equals(customName)) {
                table = new Table<>(clazz, customName);
                tableMap.put(clazz, table);
                table.initialize(this);
            }
            return table;
        }
    }
}
