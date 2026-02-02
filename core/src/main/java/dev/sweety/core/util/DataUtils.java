package dev.sweety.core.util;

import java.lang.reflect.Field;

public class DataUtils {

    public static <Type> Object get(Class<Type> instanceClass, String fieldName, Object instance) throws Exception {
        Type castInstance = instanceClass.cast(instance);
        Field field = instanceClass.getDeclaredField(fieldName);
        if (!field.canAccess(castInstance)) field.setAccessible(true);
        return field.get(castInstance);
    }

    public static <Type> Object get(Class<Type> instanceClass, String fieldName) throws Exception {
        Field field = instanceClass.getDeclaredField(fieldName);
        if (!field.canAccess(null)) field.setAccessible(true);
        return field.get(null);
    }

    public static <Type, Value> void set(Class<Type> instanceClass, String fieldName, Object instance, Value value) throws Exception {
        Type castInstance = instanceClass.cast(instance);
        Field field = instanceClass.getDeclaredField(fieldName);
        if (!field.canAccess(castInstance)) field.setAccessible(true);
        field.set(castInstance, value);
    }

    public static <Type, Value> void set(Class<Type> instanceClass, String fieldName, Value value) throws Exception {
        Field field = instanceClass.getDeclaredField(fieldName);
        if (!field.canAccess(null)) field.setAccessible(true);
        field.set(null, value);
    }

    public static RuntimeException sneakyThrow(Throwable t) {
        if (t == null) throw new NullPointerException("t");
        return DataUtils.sneakyThrow0(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow0(Throwable t) throws T {
        throw (T) t;
    }

}
