package dev.sweety.data;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ObjectUtils {

    @SafeVarargs
    public static <T> boolean isNull(T t, Predicate<T>... predicates) {
        if (t == null || t instanceof CharSequence c && c.isEmpty()) return true;
        return Arrays.stream(predicates).anyMatch(predicate -> predicate.test(t));
    }

    @SafeVarargs
    public static <T> @NotNull T nullOption(T t, @NotNull T fallback, Predicate<T>... predicates) {
        return isNull(t, predicates) ? fallback : t;
    }

    @SafeVarargs
    public static <T, R> @NotNull R nullOption(T t, Function<@NotNull T, R> getter, @NotNull R fallback, Predicate<T>... predicates) {
        return isNull(t, predicates) ? fallback : getter.apply(t);
    }

    @SafeVarargs
    public static <E> E getByOrdinalMod(int ordinal, E... values) {
        return values[Math.abs(ordinal) % values.length];
    }

    public static <E extends Enum<E>> E getByOrdinalMod(int ordinal, Class<E> clazz) {
        return getByOrdinalMod(ordinal, clazz.getEnumConstants());
    }

    public static <E extends Enum<E>> E getByName(String name, Class<E> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public static <T> T make(Supplier<T> factory) {
        return factory.get();
    }

    public static <T> T make(T object, Consumer<T> initializer) {
        initializer.accept(object);
        return object;
    }

}
