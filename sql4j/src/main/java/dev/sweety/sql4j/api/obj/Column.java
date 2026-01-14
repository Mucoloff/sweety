package dev.sweety.sql4j.api.obj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public record Column(String name, Field field, Info info) {

    public Column {
        field.setAccessible(true);
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
        ps.setObject(index, value);
    }

    public void set(Object instance, Object value) {
        try {
            Class<?> type = field.getType();
            if (value instanceof Number n) {
                if (type == byte.class) field.set(instance, n.byteValue());
                else if (type == short.class) field.set(instance, n.shortValue());
                else if (type == int.class) field.set(instance, n.intValue());
                else if (type == long.class) field.set(instance, n.longValue());
                else if (type == float.class) field.set(instance, n.floatValue());
                else if (type == double.class) field.set(instance, n.doubleValue());
                else field.set(instance, value); // leave Number as is for wrappers
            } else if (type == boolean.class && value instanceof Boolean b) {
                field.set(instance, b);
            } else field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }


    public boolean isPrimaryKey() {
        return info.primaryKey();
    }

    public boolean isAutoIncrement() {
        return info.autoIncrement();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Info {
        String name() default "";

        boolean primaryKey() default false;

        boolean autoIncrement() default false;
    }
}
