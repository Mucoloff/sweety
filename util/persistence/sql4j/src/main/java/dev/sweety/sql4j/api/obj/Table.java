package dev.sweety.sql4j.api.obj;

import dev.sweety.sql4j.api.obj.annotation.Default;
import dev.sweety.sql4j.api.obj.annotation.Index;
import dev.sweety.sql4j.api.obj.annotation.ManyToMany;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;
import dev.sweety.sql4j.api.obj.annotation.SoftDelete;
import dev.sweety.sql4j.api.obj.annotation.Unique;
import dev.sweety.sql4j.api.obj.table.TableRegistry;


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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Table<T> {
    private final String name;
    private final Class<T> clazz;

    private final List<Column<?>> columnsList = new ArrayList<>();
    private final Map<String, Column<?>> columnsMap = new LinkedHashMap<>();
    private final List<Column<?>> primaryKeys = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();
    private final List<Column<?>> updatableColumns = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    private InsertableColumns insertableColumns;
    private Column<?> softDeleteColumn;
    private final TableAccessor<T> accessor;

    private volatile boolean initializing = false;
    private volatile boolean initialized = false;

    public Table(Class<T> clazz, String name) {
        this.clazz = clazz;
        this.name = name;
        this.accessor = discoverAccessor(clazz);
    }

    private TableAccessor<T> discoverAccessor(Class<T> clazz) {
        String name = clazz.getName();
        // Try standard naming (e.g., UserTable)
        TableAccessor<T> accessor = tryLoadAccessor(name + "Table");
        if (accessor != null) return accessor;

        // Try nested naming replacement (e.g., EnterpriseDslTest_UserTable)
        if (name.contains("$")) {
            accessor = tryLoadAccessor(name.replace('$', '_') + "Table");
            return accessor;
        }
        return null;
    }

    private TableAccessor<T> tryLoadAccessor(String className) {
        try {
            Class<?> accessorClass = Class.forName(className);
            java.lang.reflect.Field instanceField = accessorClass.getField("INSTANCE");
            //noinspection unchecked
            return (TableAccessor<T>) instanceField.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    public TableAccessor<T> accessor() {
        return accessor;
    }

    public void initialize(TableRegistry registry) {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            if (initializing) throw new IllegalStateException(
                    "Circular initialization detected for table '" + name +
                    "'. Check for circular @ForeignKey references.");
            initializing = true;

            try {
                // If we have an accessor, we can potentially skip some reflection
                // (Future: populate columns list directly from accessor constants)

                // Pass 1: Basic columns
                for (Field field : clazz.getDeclaredFields()) {
                    Column.Info colInfo = field.getAnnotation(Column.Info.class);
                    if (colInfo != null) {
                        if (colInfo.nullable() && field.getType().isPrimitive()) {
                            throw new IllegalStateException(
                                    "Column '" + (colInfo.name().isEmpty() ? field.getName() : colInfo.name()) +
                                    "' in table '" + name + "' is marked nullable but its field type '" +
                                    field.getType().getName() + "' is a primitive.");
                        }

                        Column<?> col = new Column<>(this, colInfo.name().isEmpty() ? field.getName() : colInfo.name(), field, colInfo);
                        
                        // Enterprise annotations
                        Unique unique = field.getAnnotation(Unique.class);
                        if (unique != null) col.setUnique(true);
                        
                        Index index = field.getAnnotation(Index.class);
                        if (index != null) col.setIndexName(index.name().isEmpty() ? "idx_" + name + "_" + col.name() : index.name());
                        
                        Default def = field.getAnnotation(Default.class);
                        if (def != null) col.setDefaultValue(def.value());
                        
                        SoftDelete soft = field.getAnnotation(SoftDelete.class);
                        if (soft != null) {
                            col.setSoftDelete(true);
                            this.softDeleteColumn = col;
                        }

                        this.columnsList.add(col);
                        this.columnsMap.put(col.name().toLowerCase(java.util.Locale.ENGLISH), col);
                        if (col.isPrimaryKey()) this.primaryKeys.add(col);
                    }
                }

                // Pass 2: Relations and Foreign Keys
                for (Field field : clazz.getDeclaredFields()) {
                    ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                    OneToMany oneToMany = field.getAnnotation(OneToMany.class);
                    ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);

                    if (manyToOne != null) {
                        Table<?> refTable = registry.get(field.getType());
                        if (refTable.primaryKeys().isEmpty()) {
                            throw new IllegalStateException("Table " + refTable.name() + " has no primary keys");
                        }
                        Column<?> refPk = refTable.primaryKeys().get(0);
                        
                        String colName = manyToOne.columnName().isEmpty() ? field.getName() + "_id" : manyToOne.columnName();
                        Column<?> col = new Column<>(this, colName, field, null, refPk.field());
                        
                        this.columnsList.add(col);
                        this.columnsMap.put(col.name().toLowerCase(java.util.Locale.ENGLISH), col);
                        
                        this.foreignKeys.add(new ForeignKey(col, refTable, refPk, true, manyToOne.onDelete(), manyToOne.onUpdate()));
                        relations.add(new Relation(Relation.Type.MANY_TO_ONE, field, field.getType(), null, null, col));
                    } else if (oneToMany != null) {
                        relations.add(new Relation(Relation.Type.ONE_TO_MANY, field, getGenericType(field), oneToMany.mappedBy(), null, null));
                    } else if (manyToMany != null) {
                        Class<?> targetClass = getGenericType(field);
                        Table<?> targetTable = registry.get(targetClass);
                        String junctionTableName = manyToMany.joinTable().isEmpty()
                                ? this.name() + "_" + field.getName()
                                : manyToMany.joinTable();

                        registry.registerJunctionTable(junctionTableName, this, targetTable);
                        relations.add(new Relation(Relation.Type.MANY_TO_MANY, field, targetClass, null, junctionTableName, null));
                    } else {
                        // Check for explicit ForeignKey info on normal columns
                        Column.Info colInfo = field.getAnnotation(Column.Info.class);
                        if (colInfo != null) {
                            dev.sweety.sql4j.api.obj.ForeignKey.Info fkInfo = field.getAnnotation(dev.sweety.sql4j.api.obj.ForeignKey.Info.class);
                            if (fkInfo != null) {
                                Table<?> refTable = registry.get(fkInfo.table());
                                Column<?> refCol = refTable.column(fkInfo.column());
                                this.foreignKeys.add(new ForeignKey(this.column(colInfo.name().isEmpty() ? field.getName() : colInfo.name()), refTable, refCol, true, fkInfo.onDelete(), fkInfo.onUpdate()));
                            }
                        }
                    }
                }

                // Finalize insertable/updatable
                Column<?> autoInc = null;
                List<Column<?>> insertColumns = new ArrayList<>();
                for (Column<?> c : columnsList) {
                    if (c.isAutoIncrement()) {
                        System.err.println("[DEBUG] TABLE " + name + " found AutoInc column: " + c.name());
                        autoInc = c;
                    } else {
                        insertColumns.add(c);
                        if (!c.isPrimaryKey()) updatableColumns.add(c);
                    }
                }
                if (autoInc == null) {
                    System.err.println("[DEBUG] TABLE " + name + " NO AutoInc column found.");
                }
                this.insertableColumns = new InsertableColumns(insertColumns, autoInc);

                initialized = true;
            } finally {
                initializing = false;
            }
        }
    }

    private Class<?> getGenericType(Field field) {
        if (java.util.Collection.class.isAssignableFrom(field.getType())) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) field.getGenericType();
            return (Class<?>) pt.getActualTypeArguments()[0];
        }
        return field.getType();
    }

    public int bindColumns(PreparedStatement ps, List<Column<?>> cols, Object instance, int startIdx) throws SQLException {
        int idx = startIdx;
        for (Column<?> c : cols) c.set(ps, idx++, instance);
        return idx;
    }

    public String name() {
        return name;
    }

    public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
        return dialect.escape(name);
    }

    public Class<T> clazz() {
        return clazz;
    }

    public static Table<Object> virtual(String name, List<Function<Table<Object>, Column<?>>> columnFactories, List<ForeignKey> foreignKeys) {
        Table<Object> table = new Table<>(Object.class, name);
        List<Column<?>> columns = columnFactories.stream().map(f -> f.apply(table)).collect(Collectors.toList());
        table.columnsList.addAll(columns);
        for (Column<?> c : columns) table.columnsMap.put(c.name().toLowerCase(java.util.Locale.ENGLISH), c);
        table.foreignKeys.addAll(foreignKeys);
        
        List<Column<?>> insertColumns = new ArrayList<>();
        Column<?> autoInc = null;
        for (Column<?> c : columns) {
            if (c.isAutoIncrement()) autoInc = c;
            else insertColumns.add(c);
        }
        table.insertableColumns = new InsertableColumns(insertColumns, autoInc);
        
        table.initialized = true;
        return table;
    }

    public List<Column<?>> columns() {
        return columnsList;
    }

    public List<Relation> relations() {
        return relations;
    }

    public List<Column<?>> primaryKeys() {
        return primaryKeys;
    }

    public List<ForeignKey> foreignKeys() {
        return foreignKeys;
    }

    public List<Column<?>> updatableColumns() {
        return updatableColumns;
    }

    public InsertableColumns insertableColumns() {
        return insertableColumns;
    }

    public Column<?> softDeleteColumn() {
        return softDeleteColumn;
    }

    public Column<?> column(String name) {
        Column<?> col = columnsMap.get(name.toLowerCase(Locale.ENGLISH));
        if (col == null) {
            // Check if it's a relation column
            for (Relation rel : relations) {
                if (rel.type() == Relation.Type.MANY_TO_ONE && rel.column().name().equalsIgnoreCase(name)) {
                    return rel.column();
                }
            }
            throw new IllegalArgumentException("Column " + name + " not found in " + this.name);
        }
        return col;
    }

    public record Relation(
            Type type,
            Field field,
            Class<?> targetClass,
            String mappedBy,
            String joinTable,
            Column<?> column // Only for MANY_TO_ONE
    ) {
        public enum Type {
            MANY_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Info {
        String name();
    }
}
