package dev.sweety.sql4j.api.obj.table;

import dev.sweety.sql4j.api.obj.Table;
import lombok.experimental.UtilityClass;

import java.util.IdentityHashMap;

@UtilityClass
public class TableRegistry {

    private final IdentityHashMap<Class<?>, Table<?>> tableMap = new IdentityHashMap<>();

    public void register(Table<?> table) {
        tableMap.put(table.clazz(), table);
    }

    public Table<?> get(Class<?> table) {
        return tableMap.get(table);
    }
}
