package dev.sweety.core.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@UtilityClass
public class ObjectUtils {

    @SafeVarargs
    public <T> boolean isNull(T t, Predicate<T>... predicates) {
        if (t == null) return true;
        if (t instanceof CharSequence c && c.isEmpty()) return true;
        for (Predicate<T> predicate : predicates) if (predicate.test(t)) return true;
        return false;
    }

    @SafeVarargs
    public <T> @NotNull T nullOption(T t, @NotNull T fallback, Predicate<T>... predicates) {
        return isNull(t, predicates) ? fallback : t;
    }

    @SafeVarargs
    public <T, R> @NotNull R nullOption(T t, Function<@NotNull T, R> getter, @NotNull R fallback, Predicate<T>... predicates) {
        return isNull(t, predicates) ? fallback : getter.apply(t);
    }

    @SafeVarargs
    public <E> E getByOrdinalMod(int ordinal, E... values) {
        return values[Math.abs(ordinal) % values.length];
    }

    public <E extends Enum<E>> E getByOrdinalMod(int ordinal, Class<E> clazz) {
        return getByOrdinalMod(ordinal, clazz.getEnumConstants());

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
