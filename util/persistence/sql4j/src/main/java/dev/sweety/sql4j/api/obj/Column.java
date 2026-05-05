package dev.sweety.sql4j.api.obj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class Column {
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

    public String name() { return name; }
    public Field field() { return field; }
    public Info info() { return info; }
    public Table<?> table() { return table; }
    public boolean isUnique() { return unique || (info != null && info.unique()); }
    public String indexName() { return indexName; }
    public String defaultValue() { return defaultValue != null ? defaultValue : (info != null ? info.defaultValue() : null); }
    public boolean isSoftDelete() { return softDelete; }

    public void setUnique(boolean unique) { this.unique = unique; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public void setSoftDelete(boolean softDelete) { this.softDelete = softDelete; }

    public Class<?> type() {
        return relationIdField != null ? relationIdField.getType() : field.getType();
    }

    public <T> T get(Object instance) {
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
        try {
            Class<?> type = field.getType();
            if (relationIdField != null && value != null && !type.isInstance(value)) {
                // If this is a relation field (e.g. User user) and we're trying to set an ID (Integer),
                // we skip it to avoid IllegalAccessException. 
                // Future versions could instantiate a proxy/stub here.
                return;
            }
            if (value == null) {
                if (type.isPrimitive()) {
                    field.set(instance, defaultPrimitiveValue(type));
                } else {
                    field.set(instance, null);
                }
                return;
            }
            
            // Handle boolean/Boolean specially (databases often return 0/1 as Integer/Long)
            if (type == boolean.class || type == Boolean.class) {
                if (value instanceof Boolean b) {
                    field.set(instance, b);
                    return;
                } else if (value instanceof Number n) {
                    field.set(instance, n.intValue() != 0);
                    return;
                }
            }

            if (value instanceof Number n) {
                if (type == byte.class || type == Byte.class) field.set(instance, n.byteValue());
                else if (type == short.class || type == Short.class) field.set(instance, n.shortValue());
                else if (type == int.class || type == Integer.class) field.set(instance, n.intValue());
                else if (type == long.class || type == Long.class) field.set(instance, n.longValue());
                else if (type == float.class || type == Float.class) field.set(instance, n.floatValue());
                else if (type == double.class || type == Double.class) field.set(instance, n.doubleValue());
                else if (type == java.math.BigDecimal.class) field.set(instance, java.math.BigDecimal.valueOf(n.doubleValue()));
                else field.set(instance, value);
            } else if (type.isEnum() && value instanceof String s) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object enumValue = Enum.valueOf((Class<Enum>) type, s);
                field.set(instance, enumValue);
            } else if (type == java.util.UUID.class) {
                if (value instanceof java.util.UUID) field.set(instance, value);
                else if (value instanceof String s) field.set(instance, java.util.UUID.fromString(s));
                else if (value instanceof byte[] b && b.length == 16) {
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
                    field.set(instance, new java.util.UUID(bb.getLong(), bb.getLong()));
                }
            } else if (type == java.time.LocalDate.class) {
                if (value instanceof java.time.LocalDate) field.set(instance, value);
                else if (value instanceof java.sql.Date d) field.set(instance, d.toLocalDate());
                else if (value instanceof String s) field.set(instance, java.time.LocalDate.parse(s));
            } else if (type == java.time.LocalDateTime.class) {
                if (value instanceof java.time.LocalDateTime) field.set(instance, value);
                else if (value instanceof java.sql.Timestamp t) field.set(instance, t.toLocalDateTime());
                else if (value instanceof String s) field.set(instance, java.time.LocalDateTime.parse(s.replace(" ", "T")));
            } else if (type == java.math.BigDecimal.class && value instanceof String s) {
                field.set(instance, new java.math.BigDecimal(s));
            } else {
                field.set(instance, value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to set field " + field.getName() + " on " + instance.getClass().getName() + " with value " + value + " of type " + (value != null ? value.getClass().getName() : "null"), e);
        }
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

        /**
         * Marks this column as nullable in the DDL (omits NOT NULL constraint).
         * <p><b>Note:</b> Cannot be {@code true} on primitive fields ({@code int}, {@code long}, etc.)
         * — use wrapper types ({@code Integer}, {@code Long}) instead.
         * This is validated at table initialization and will throw {@link IllegalStateException}.
         */
        boolean nullable() default false;

        boolean unique() default false;

        String defaultValue() default "";
    }
}
