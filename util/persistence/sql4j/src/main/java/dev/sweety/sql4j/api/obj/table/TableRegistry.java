package dev.sweety.sql4j.api.obj.table;

import dev.sweety.sql4j.api.obj.Table;

import java.util.IdentityHashMap;

public final class TableRegistry {

    private static final IdentityHashMap<Class<?>, Table<?>> tableMap = new IdentityHashMap<>();

    public static void register(Table<?> table) {
        tableMap.put(table.clazz(), table);
    }

    public static Table<?> get(Class<?> table) {
        return tableMap.get(table);
    }
}
