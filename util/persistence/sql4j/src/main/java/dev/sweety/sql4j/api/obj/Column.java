package dev.sweety.sql4j.api.obj;

import dev.sweety.sql4j.api.query.Criterion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public class Column<T> {
    private final String name;
    private final Field field;
    private final Info info;
    private final Table<?> table;
    private Field relationIdField; // For ManyToOne
    private boolean unique = false;
    private String indexName = null;
    private String defaultValue = null;
    private boolean softDelete = false;

    public Column(Table<?> table, String name, Field field, Info info) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.field = Objects.requireNonNull(field, "field cannot be null");
        this.info = info;
        field.setAccessible(true);
    }

    public Column(Table<?> table, String name, Field field, Info info, Field relationIdField) {
        this(table, name, field, info);
        this.relationIdField = relationIdField;
        if (relationIdField != null) relationIdField.setAccessible(true);
    }

    protected Column(Table<?> table, String name) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.field = null;
        this.info = null;
    }

    public String name() {
        return name;
    }

    public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
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
        return Criterion.in(this, java.util.Arrays.asList(values));
    }

    public Class<?> type() {
        return relationIdField != null ? relationIdField.getType() : field.getType();
    }

    public T get(Object instance) {
        try {
            //noinspection unchecked
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public void set(PreparedStatement ps, int index, Object instance) throws SQLException {
        Object value = get(instance);
        if (value instanceof Enum<?> e) {
            ps.setObject(index, e.name());
        } else if (relationIdField != null && value != null) {
            // It's a ManyToOne relationship, extract the ID from the entity
            try {
                Object id = relationIdField.get(value);
                ps.setObject(index, id);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to extract ID from relation " + value.getClass().getName(), e);
            }
        } else {
            ps.setObject(index, value);
        }
    }

    public void set(Object instance, Object value) {
        Class<?> type = field.getType();
        if (relationIdField != null && value != null && !type.isInstance(value)) {
            // It's an ID being set to a relation field (Entity). 
            // We ignore it here as relations are handled by Join logic or Lazy loading.
            return;
        }

        value = convertValue(value, type);

        // Try using the accessor first (to avoid reflection)
        TableAccessor<?> accessor = table.accessor();
        if (accessor != null) {
            //noinspection unchecked
            ((TableAccessor<Object>) accessor).setFieldValue(instance, name, value);
            return;
        }

        try {
            if (value == null && type.isPrimitive()) {
                field.set(instance, defaultPrimitiveValue(type));
            } else {
                field.set(instance, value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object convertValue(Object value, Class<?> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return value;

        if (type == boolean.class || type == Boolean.class) {
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.intValue() != 0;
            if (value instanceof String s) return s.equalsIgnoreCase("true") || s.equals("1");
        }

        if (value instanceof Number n) {
            if (type == byte.class || type == Byte.class) return n.byteValue();
            if (type == short.class || type == Short.class) return n.shortValue();
            if (type == int.class || type == Integer.class) return n.intValue();
            if (type == long.class || type == Long.class) return n.longValue();
            if (type == float.class || type == Float.class) return n.floatValue();
            if (type == double.class || type == Double.class) return n.doubleValue();
            if (type == java.math.BigDecimal.class) return java.math.BigDecimal.valueOf(n.doubleValue());
        }

        if (type.isEnum() && value instanceof String s) {
            //noinspection unchecked,rawtypes
            return Enum.valueOf((Class<Enum>) type, s);
        }

        if (type == java.util.UUID.class) {
            if (value instanceof String s) return java.util.UUID.fromString(s);
            if (value instanceof byte[] b && b.length == 16) {
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
                return new java.util.UUID(bb.getLong(), bb.getLong());
            }
        }

        if (type == java.time.LocalDate.class) {
            if (value instanceof java.sql.Date d) return d.toLocalDate();
            if (value instanceof String s) return java.time.LocalDate.parse(s);
        }

        if (type == java.time.LocalDateTime.class) {
            if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime();
            if (value instanceof String s) return java.time.LocalDateTime.parse(s.replace(" ", "T"));
        }

        if (type == java.math.BigDecimal.class && value instanceof String s) {
            return new java.math.BigDecimal(s);
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
        return info != null && info.primaryKey();
    }

    public boolean isAutoIncrement() {
        return info != null && info.autoIncrement();
    }

    public boolean isNullable() {
        return info != null && info.nullable();
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
