package dev.sweety.sql4j.api.obj.table;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ForeignKey;
import dev.sweety.sql4j.api.obj.ForeignKey.Action;
import dev.sweety.sql4j.api.obj.Table;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class TableRegistry {

    private final Map<Class<?>, Table<?>> tableMap = new IdentityHashMap<>();
    private static final TableRegistry DEFAULT = new TableRegistry();

    public static TableRegistry getDefault() {
        return DEFAULT;
    }

    private final Map<String, Table<?>> allTables = new HashMap<>();

    public void register(Table<?> table) {
        Objects.requireNonNull(table, "table cannot be null");
        synchronized (tableMap) {
            tableMap.put(table.clazz(), table);
            allTables.put(table.name().toLowerCase(Locale.ENGLISH), table);
        }
    }

    public <T> Table<T> get(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz cannot be null");
        synchronized (tableMap) {
            //noinspection unchecked
            Table<T> table = (Table<T>) tableMap.get(clazz);
            if (table == null) {
                // Try to load the generated Table instance (which should self-register)
                try {
                    String mirrorName = clazz.getName() + "Table";
                    Class<?> mirrorClass = Class.forName(mirrorName);
                    // Accessing mirrorClass should trigger its static block and register the instance
                    Field instanceField = mirrorClass.getField("INSTANCE");
                    table = (Table<T>) instanceField.get(null);
                    if (table != null) {
                        return table;
                    }
                } catch (Exception ignored) {
                    // Fallback to reflection if no mirror exists
                }

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

    public Collection<Table<?>> allTables() {
        synchronized (tableMap) {
            return new ArrayList<>(allTables.values());
        }
    }

    public void registerJunctionTable(String name, Table<?> t1, Table<?> t2) {
        synchronized (tableMap) {
            String key = name.toLowerCase(Locale.ENGLISH);
            if (allTables.containsKey(key)) return;

            Column<?> pk1 = t1.primaryKeys().getFirst();
            Column<?> pk2 = t2.primaryKeys().getFirst();

            List<Function<Table<Object>, Column<?>>> colFactories = List.of(
                t -> new Column<>(t, t1.name().toLowerCase() + "_id", pk1.field(), null),
                t -> new Column<>(t, t2.name().toLowerCase() + "_id", pk2.field(), null)
            );

            List<ForeignKey> fks = new ArrayList<>();
            Table<Object> junctionTable = Table.virtual(name, colFactories, fks);
            
            fks.add(new ForeignKey(junctionTable.columns().get(0), t1, pk1, false, Action.CASCADE, ForeignKey.Action.CASCADE));
            fks.add(new ForeignKey(junctionTable.columns().get(1), t2, pk2, false, ForeignKey.Action.CASCADE, ForeignKey.Action.CASCADE));

            allTables.put(key, junctionTable);
        }
    }
}
