package dev.sweety.core.util;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.function.Supplier;

@UtilityClass
public class ObjectUtils {

    public <T> boolean isNull(T t) {
        if (t == null) return true;
        if (t instanceof CharSequence c && c.isEmpty()) return true;
        return false;
    }

    public <T> T nullOption(T t, T fallback) {
        return isNull(t) ? fallback : t;
    }

    public <E extends Enum<E>> E getByName(String name, Class<E> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
                     .filter(e -> e.name().equalsIgnoreCase(name))
                     .findFirst().orElse(null);
    }

    public static <T> T make(Supplier<T> factory) {
        return factory.get();
    }

    public static <T> T make(T object, java.util.function.Consumer<T> initializer) {
        initializer.accept(object);
        return object;
    }

}
