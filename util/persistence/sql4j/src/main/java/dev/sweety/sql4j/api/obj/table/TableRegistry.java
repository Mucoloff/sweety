package dev.sweety.sql4j.api.obj.table;

import dev.sweety.sql4j.api.obj.Table;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TableRegistry {

    private final Map<Class<?>, Table<?>> tableMap = new IdentityHashMap<>();
    private final Map<String, Table<?>> allTables = new java.util.HashMap<>();

    public void register(Table<?> table) {
        synchronized (tableMap) {
            tableMap.put(table.clazz(), table);
            allTables.put(table.name().toLowerCase(java.util.Locale.ENGLISH), table);
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
                    register(table);
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
                register(table);
                table.initialize(this);
            }
            return table;
        }
    }

    public java.util.Collection<Table<?>> allTables() {
        synchronized (tableMap) {
            return new java.util.ArrayList<>(allTables.values());
        }
    }

    public void registerJunctionTable(String name, Table<?> t1, Table<?> t2) {
        synchronized (tableMap) {
            String key = name.toLowerCase(java.util.Locale.ENGLISH);
            if (allTables.containsKey(key)) return;

            dev.sweety.sql4j.api.obj.Column pk1 = t1.primaryKeys().get(0);
            dev.sweety.sql4j.api.obj.Column pk2 = t2.primaryKeys().get(0);

            dev.sweety.sql4j.api.obj.Column c1 = new dev.sweety.sql4j.api.obj.Column(t1.name().toLowerCase() + "_id", pk1.field(), null);
            dev.sweety.sql4j.api.obj.Column c2 = new dev.sweety.sql4j.api.obj.Column(t2.name().toLowerCase() + "_id", pk2.field(), null);

            java.util.List<dev.sweety.sql4j.api.obj.Column> cols = java.util.List.of(c1, c2);
            java.util.List<dev.sweety.sql4j.api.obj.ForeignKey> fks = java.util.List.of(
                new dev.sweety.sql4j.api.obj.ForeignKey(c1, t1, pk1, false, dev.sweety.sql4j.api.obj.ForeignKey.Action.CASCADE, dev.sweety.sql4j.api.obj.ForeignKey.Action.CASCADE),
                new dev.sweety.sql4j.api.obj.ForeignKey(c2, t2, pk2, false, dev.sweety.sql4j.api.obj.ForeignKey.Action.CASCADE, dev.sweety.sql4j.api.obj.ForeignKey.Action.CASCADE)
            );

            Table<Object> junctionTable = Table.virtual(name, cols, fks);
            allTables.put(key, junctionTable);
        }
    }
}
