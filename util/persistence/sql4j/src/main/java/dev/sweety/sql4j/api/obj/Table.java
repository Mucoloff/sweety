package dev.sweety.sql4j.api.obj;

import dev.sweety.sql4j.api.obj.annotation.ManyToMany;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;
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

public final class Table<T> {
    private final String name;
    private final Class<T> clazz;

    private final List<Column> columnsList = new ArrayList<>();
    private final Map<String, Column> columnsMap = new LinkedHashMap<>();
    private final List<Column> primaryKeys = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();
    private final List<Column> updatableColumns = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    private InsertableColumns insertableColumns;

    private volatile boolean initializing = false;
    private volatile boolean initialized = false;

    public Table(Class<T> clazz, String name) {
        this.clazz = clazz;
        this.name = name;
    }

    public void initialize(TableRegistry registry) {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            if (initializing) throw new IllegalStateException(
                    "Circular initialization detected for table '" + name +
                    "'. Check for circular @ForeignKey references.");
            initializing = true;

            List<Column> cols = new ArrayList<>();
            List<Column> pks = new ArrayList<>();
            List<ForeignKey> fks = new ArrayList<>();

            // Pass 1: Basic columns (no registry calls)
            for (Field field : clazz.getDeclaredFields()) {
                Column.Info colInfo = field.getAnnotation(Column.Info.class);
                if (colInfo != null) {
                    if (colInfo.nullable() && field.getType().isPrimitive()) {
                        initializing = false;
                        throw new IllegalStateException(
                                "Column '" + (colInfo.name().isEmpty() ? field.getName() : colInfo.name()) +
                                "' in table '" + name + "' is marked nullable but its field type '" +
                                field.getType().getName() + "' is a primitive.");
                    }

                    Column col = new Column(colInfo.name().isEmpty() ? field.getName() : colInfo.name(), field, colInfo);
                    cols.add(col);
                    this.columnsMap.put(col.name().toLowerCase(java.util.Locale.ENGLISH), col);
                    if (col.isPrimaryKey()) pks.add(col);
                }
            }
            this.columnsList.addAll(cols);
            this.primaryKeys.addAll(pks);

            // Pass 2: Relations and Foreign Keys (registry calls safe now)
            for (Field field : clazz.getDeclaredFields()) {
                Column.Info colInfo = field.getAnnotation(Column.Info.class);
                ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                OneToMany oneToMany = field.getAnnotation(OneToMany.class);
                ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);

                if (colInfo != null) {
                    ForeignKey.Info fkInfo = field.getAnnotation(ForeignKey.Info.class);
                    if (fkInfo != null) {
                        Table<?> refTable = registry.get(fkInfo.table());
                        Column refCol = refTable.column(fkInfo.column());
                        fks.add(new ForeignKey(this.column(colInfo.name().isEmpty() ? field.getName() : colInfo.name()), refTable, refCol, true, fkInfo.onDelete(), fkInfo.onUpdate()));
                    }
                } else if (manyToOne != null) {
                    Table<?> refTable = registry.get(field.getType());
                    if (refTable.primaryKeys().isEmpty()) {
                        throw new IllegalStateException("Table " + refTable.name() + " has no primary keys (circularity or missing @Column.Info)");
                    }
                    Column refPk = refTable.primaryKeys().get(0);
                    
                    String colName = manyToOne.columnName().isEmpty() ? field.getName() + "_id" : manyToOne.columnName();
                    Column col = new Column(colName, field, null, refPk.field());
                    
                    this.columnsList.add(col);
                    this.columnsMap.put(col.name().toLowerCase(java.util.Locale.ENGLISH), col);
                    
                    fks.add(new ForeignKey(col, refTable, refPk, true, manyToOne.onDelete(), manyToOne.onUpdate()));
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
                }
            }

            this.foreignKeys.addAll(fks);
            
            List<Column> updatable = new ArrayList<>();
            Column autoInc = null;
            for (Column c : columnsList) {
                if (!c.isPrimaryKey()) updatable.add(c);
                if (c.isAutoIncrement()) {
                    if (autoInc != null) {
                        initializing = false;
                        throw new IllegalStateException("Multiple autoIncrement columns not supported");
                    }
                    autoInc = c;
                }
            }
            this.updatableColumns.addAll(updatable);
            this.insertableColumns = new InsertableColumns(
                    columnsList.stream().filter(c -> !c.isAutoIncrement()).toList(),
                    autoInc
            );

            initialized = true;            // No need to reset initializing — initialized=true is the gate now
        }
    }

    private Class<?> getGenericType(Field field) {
        if (java.util.Collection.class.isAssignableFrom(field.getType())) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) field.getGenericType();
            return (Class<?>) pt.getActualTypeArguments()[0];
        }
        return field.getType();
    }

    public int bindColumns(PreparedStatement ps, List<Column> cols, Object instance, int startIdx) throws SQLException {
        int idx = startIdx;
        for (Column c : cols) c.set(ps, idx++, instance);
        return idx;
    }

    public String name() {
        return name;
    }

    public Class<T> clazz() {
        return clazz;
    }

    public static Table<Object> virtual(String name, List<Column> columns, List<ForeignKey> foreignKeys) {
        Table<Object> table = new Table<>(Object.class, name);
        table.columnsList.addAll(columns);
        for (Column c : columns) table.columnsMap.put(c.name().toLowerCase(java.util.Locale.ENGLISH), c);
        table.foreignKeys.addAll(foreignKeys);
        table.initialized = true;
        return table;
    }

    public List<Column> columns() {
        return columnsList;
    }

    public List<Relation> relations() {
        return relations;
    }

    public List<Column> primaryKeys() {
        return primaryKeys;
    }

    public List<ForeignKey> foreignKeys() {
        return foreignKeys;
    }

    public List<Column> updatableColumns() {
        return updatableColumns;
    }

    public InsertableColumns insertableColumns() {
        return insertableColumns;
    }

    public Column column(String name) {
        Column col = columnsMap.get(name.toLowerCase(Locale.ENGLISH));
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
            Column column // Only for MANY_TO_ONE
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
