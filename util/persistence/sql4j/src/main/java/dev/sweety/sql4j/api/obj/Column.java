package dev.sweety.sql4j.api.obj;

import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.util.UUIDv8Hybrid;
import dev.sweety.sql4j.api.connection.dialect.Dialect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Arrays;
import java.util.UUID;

public class Column<T> {
    private final String name;
    private final Class<T> type;
    private final Field field;
    private final Info info;
    private final Table<?> table;
    private Field relationIdField; // For ManyToOne
    private boolean relation = false;
    private boolean unique = false;
    private String indexName = null;
    private String defaultValue = null;
    private boolean softDelete = false;
    private boolean primaryKey = false;
    private boolean autoIncrement = false;
    private boolean nullable = true;
    private int ordinalIndex = -1;
    private PrimitiveKind primitiveKind = PrimitiveKind.OBJECT;

    public Column(Table<?> table, String name, Field field, Info info) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.field = field;
        //noinspection unchecked
        this.type = (Class<T>) (field != null ? field.getType() : null);
        this.info = info;
        if (field != null) field.setAccessible(true);
        this.primitiveKind = PrimitiveKind.from(type());
        if (info != null) {
            this.primaryKey = info.primaryKey();
            this.autoIncrement = info.autoIncrement();
            this.nullable = info.nullable();
            this.unique = info.unique();
            this.defaultValue = info.defaultValue().isEmpty() ? null : info.defaultValue();
        }
    }

    public Column(Table<?> table, String name, Field field, Info info, Field relationIdField) {
        this(table, name, field, info);
        this.relationIdField = relationIdField;
        if (relationIdField != null) relationIdField.setAccessible(true);
        this.primitiveKind = PrimitiveKind.from(type());
    }

    public Column(Table<?> table, String name, Class<T> type, boolean primaryKey, boolean autoIncrement, boolean nullable) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = type;
        this.field = null;
        this.info = null;
        this.primaryKey = primaryKey;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.primitiveKind = PrimitiveKind.from(type);
    }

    protected Column(Table<?> table, String name) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = null;
        this.field = null;
        this.info = null;
        this.nullable = true;
        this.primitiveKind = PrimitiveKind.OBJECT;
    }

    public int ordinalIndex() {
        return ordinalIndex;
    }

    public void setOrdinalIndex(int ordinalIndex) {
        this.ordinalIndex = ordinalIndex;
    }

    public PrimitiveKind primitiveKind() {
        return primitiveKind;
    }

    public String name() {
        return name;
    }

    public String toSql(Dialect dialect) {
        return dialect.escape(name);
    }

    public Field field() {
        return field;
    }

    public Info info() {
        return info;
    }

    public Table<?> table() {
        return table;
    }

    public boolean isUnique() {
        return unique || (info != null && info.unique());
    }

    public String indexName() {
        return indexName;
    }

    public String defaultValue() {
        return defaultValue != null ? defaultValue : (info != null ? info.defaultValue() : null);
    }

    public boolean isSoftDelete() {
        return softDelete;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setSoftDelete(boolean softDelete) {
        this.softDelete = softDelete;
    }

    // --- DSL Methods ---

    public Criterion eq(T value) {
        return Criterion.eq(this, value);
    }

    public Criterion ne(T value) {
        return Criterion.ne(this, value);
    }

    public Criterion gt(T value) {
        return Criterion.gt(this, value);
    }

    public Criterion ge(T value) {
        return Criterion.ge(this, value);
    }

    public Criterion lt(T value) {
        return Criterion.lt(this, value);
    }

    public Criterion le(T value) {
        return Criterion.le(this, value);
    }

    public Criterion like(String pattern) {
        return Criterion.like(this, pattern);
    }

    @SafeVarargs
    public final Criterion in(T... values) {
        return Criterion.in(this, Arrays.asList(values));
    }

    public Class<?> type() {
        if (type != null) return type;
        return relationIdField != null ? relationIdField.getType() : (field != null ? field.getType() : Object.class);
    }

    public boolean isRelation() {
        return relation;
    }

    public void setRelation(boolean relation) {
        this.relation = relation;
    }

    public T get(Object instance) {
        TableAccessor<?> accessor = table.accessor();
        if (accessor != null && ordinalIndex >= 0) {
            //noinspection unchecked
            TableAccessor<Object> objAccessor = (TableAccessor<Object>) accessor;
            Object val = switch (primitiveKind) {
                case BOOLEAN -> objAccessor.getBoolean(instance, ordinalIndex);
                case BYTE -> objAccessor.getByte(instance, ordinalIndex);
                case SHORT -> objAccessor.getShort(instance, ordinalIndex);
                case INT -> objAccessor.getInt(instance, ordinalIndex);
                case LONG -> objAccessor.getLong(instance, ordinalIndex);
                case FLOAT -> objAccessor.getFloat(instance, ordinalIndex);
                case DOUBLE -> objAccessor.getDouble(instance, ordinalIndex);
                case CHAR -> objAccessor.getChar(instance, ordinalIndex);
                case OBJECT -> objAccessor.getObject(instance, ordinalIndex);
            };
            //noinspection unchecked
            return (T) val;
        }

        try {
            //noinspection unchecked
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new Sql4jMappingException("Failed to read field '" + name + "' on " + instance.getClass().getName(), e);
        }
    }

    public void set(PreparedStatement ps, int index, Object instance) throws SQLException {
        Object value = get(instance);
        if (value instanceof Enum<?> e) {
            ps.setObject(index, e.name());
        } else if ((relation || relationIdField != null) && value != null && !type().isInstance(value)) {
            // It's a relation (Entity) but we need its ID for the physical column
            // Find the PK of the entity
            Table<?> refTable = TableRegistry.getDefault().get(value.getClass());
            if (refTable != null && !refTable.primaryKeys().isEmpty()) {
                Column<?> refPk = refTable.primaryKeys().getFirst();
                value = refPk.get(value);
                ps.setObject(index, value);
            } else {
                ps.setObject(index, value);
            }
        } else {
            ps.setObject(index, value);
        }
    }

    public void set(Object instance, Object value) {
        Class<?> type = type();
        if (relationIdField != null && value != null && !type.isInstance(value)) {
            // It's an ID being set to a relation field (Entity). 
            // We ignore it here as relations are handled by Join logic or Lazy loading.
            return;
        }

        value = convertValue(value, type);

        // Try using the accessor first (to avoid reflection)
        TableAccessor<?> accessor = table.accessor();
        if (accessor != null && ordinalIndex >= 0) {
            //noinspection unchecked
            TableAccessor<Object> objAccessor = (TableAccessor<Object>) accessor;
            switch (primitiveKind) {
                case BOOLEAN -> objAccessor.setBoolean(instance, ordinalIndex, value instanceof Boolean b ? b : (Boolean.parseBoolean(String.valueOf(value))));
                case BYTE -> objAccessor.setByte(instance, ordinalIndex, value instanceof Number n ? n.byteValue() : (Byte.parseByte(String.valueOf(value))));
                case SHORT -> objAccessor.setShort(instance, ordinalIndex, value instanceof Number n ? n.shortValue() : (Short.parseShort(String.valueOf(value))));
                case INT -> objAccessor.setInt(instance, ordinalIndex, value instanceof Number n ? n.intValue() : (Integer.parseInt(String.valueOf(value))));
                case LONG -> objAccessor.setLong(instance, ordinalIndex, value instanceof Number n ? n.longValue() : (Long.parseLong(String.valueOf(value))));
                case FLOAT -> objAccessor.setFloat(instance, ordinalIndex, value instanceof Number n ? n.floatValue() : (Float.parseFloat(String.valueOf(value))));
                case DOUBLE -> objAccessor.setDouble(instance, ordinalIndex, value instanceof Number n ? n.doubleValue() : (Double.parseDouble(String.valueOf(value))));
                case CHAR -> objAccessor.setChar(instance, ordinalIndex, value instanceof Character c ? c : (value != null && !value.toString().isEmpty() ? value.toString().charAt(0) : '\0'));
                case OBJECT -> objAccessor.setObject(instance, ordinalIndex, value);
            }
            return;
        }

        try {
            if (value == null && type.isPrimitive()) {
                field.set(instance, defaultPrimitiveValue(type));
            } else {
                field.set(instance, value);
            }
        } catch (IllegalAccessException e) {
            throw new Sql4jMappingException("Failed to set field '" + name + "' on " + instance.getClass().getName(), e);
        }
    }

    public static Object convertValue(Object value, Class<?> type) {
        if (value == null) return null;
        if (type == null || type.isInstance(value)) return value;

        if (type == boolean.class || type == Boolean.class) {
            switch (value) {
                case Boolean b -> {
                    return b;
                }
                case Number n -> {
                    return n.intValue() != 0;
                }
                case String s -> {
                    return s.equalsIgnoreCase("true") || s.equals("1");
                }
                default -> {
                }
            }
        } else if (value instanceof Number n) {
            if (type == byte.class || type == Byte.class) return n.byteValue();
            if (type == short.class || type == Short.class) return n.shortValue();
            if (type == int.class || type == Integer.class) return n.intValue();
            if (type == long.class || type == Long.class) return n.longValue();
            if (type == float.class || type == Float.class) return n.floatValue();
            if (type == double.class || type == Double.class) return n.doubleValue();
            if (type == BigDecimal.class) return BigDecimal.valueOf(n.doubleValue());
        } else if (type.isEnum() && value instanceof String s) {
            //noinspection unchecked,rawtypes
            return Enum.valueOf((Class<Enum>) type, s.toUpperCase());
        } else if (type == UUID.class) {
            if (value instanceof String s) return UUID.fromString(s);
            if (value instanceof byte[] b && b.length == 16) {
                return UUIDv8Hybrid.fromBytes(b);
            }
        } else if (type == LocalDate.class) {
            if (value instanceof Date d) return d.toLocalDate();
            if (value instanceof String s) return LocalDate.parse(s);
        } else if (type == LocalDateTime.class) {
            if (value instanceof Timestamp t) return t.toLocalDateTime();
            if (value instanceof String s) return LocalDateTime.parse(s.replace(" ", "T"));
        } else if (type == BigDecimal.class && value instanceof String s) {
            return new BigDecimal(s);
        }
        return value;
    }

    private static Object defaultPrimitiveValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public boolean isNullable() {
        return nullable;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Info {
        String name() default "";

        boolean primaryKey() default false;

        boolean autoIncrement() default false;

        boolean nullable() default false;

        boolean unique() default false;

        String defaultValue() default "";
    }
}
