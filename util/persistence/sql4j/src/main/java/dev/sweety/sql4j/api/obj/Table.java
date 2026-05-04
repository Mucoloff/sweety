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
    private final String name;
    private final Class<T> clazz;

    private final List<Column> columnsList = new ArrayList<>();
    private final Map<String, Column> columnsMap = new LinkedHashMap<>();
    private final List<Column> primaryKeys = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();
    private final List<Column> updatableColumns = new ArrayList<>();
    private Pair<List<Column>, Column> insertableColumns;

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

            for (Field field : clazz.getDeclaredFields()) {
                Column.Info colInfo = field.getAnnotation(Column.Info.class);
                if (colInfo == null) continue;

                // Validate: nullable primitive is a configuration error
                if (colInfo.nullable() && field.getType().isPrimitive()) {
                    initializing = false;
                    throw new IllegalStateException(
                            "Column '" + (colInfo.name().isEmpty() ? field.getName() : colInfo.name()) +
                            "' in table '" + name + "' is marked nullable but its field type '" +
                            field.getType().getName() + "' is a primitive. " +
                            "Use the wrapper type (e.g. Integer instead of int).");
                }

                Column col = new Column(colInfo.name().isEmpty() ? field.getName() : colInfo.name(), field, colInfo);
                cols.add(col);
                this.columnsMap.put(col.name(), col);
                if (col.isPrimaryKey()) pks.add(col);

                ForeignKey.Info fkInfo = field.getAnnotation(ForeignKey.Info.class);
                if (fkInfo != null) {
                    Table<?> refTable = registry.get(fkInfo.table());
                    Column refCol = refTable.column(fkInfo.column());
                    fks.add(new ForeignKey(col, refTable, refCol, true, fkInfo.onDelete(), fkInfo.onUpdate()));
                }
            }

            List<Column> updatable = new ArrayList<>();
            Column autoInc = null;
            for (Column c : cols) {
                if (!c.isPrimaryKey()) updatable.add(c);
                if (c.isAutoIncrement()) {
                    if (autoInc != null) {
                        initializing = false;
                        throw new IllegalStateException("Multiple autoIncrement columns not supported");
                    }
                    autoInc = c;
                }
            }

            this.columnsList.addAll(cols);
            this.primaryKeys.addAll(pks);
            this.foreignKeys.addAll(fks);
            this.updatableColumns.addAll(updatable);
            this.insertableColumns = Pair.of(
                    cols.stream().filter(c -> !c.isAutoIncrement()).toList(),
                    autoInc
            );

            initialized = true;
            // No need to reset initializing — initialized=true is the gate now
        }
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

    public List<Column> columns() {
        return columnsList;
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

    public Pair<List<Column>, Column> insertableColumns() {
        return insertableColumns;
    }

    public Column column(String name) {
        return columnsMap.get(name);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Info {
        String name();
    }
}
