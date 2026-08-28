package dev.sweety.data;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ObjectUtils {

    public static <T> T validate(T t, Predicate<T> predicate, String npe, String iae) {
        if (predicate.test(Objects.requireNonNull(t, npe))) throw new IllegalArgumentException(iae);
        return t;
    }

    @SafeVarargs
    public static <T> boolean notNull(T t, Predicate<T>... predicates) {
        return !isNull(t, predicates);
    }

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

    public static <E> E getByOrdinalMod(int ordinal, Iterable<? extends E> values) {
        if (values == null || !values.iterator().hasNext()) return null;
        int absIndex = Math.abs(ordinal);
        if (values instanceof List<? extends E> list) return list.get(absIndex % list.size());
        int index = values instanceof Collection<? extends E> collection ? absIndex % collection.size() : absIndex;
        Iterator<? extends E> iter = values.iterator();
        for (int i = 0; i < index; i++) iter.next();
        return iter.next();
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

    public static <T> T create(Supplier<T> factory, Consumer<T> initializer) {
        var object = factory.get();
        initializer.accept(object);
        return object;
    }

    public static <T> T make(T object, Consumer<T> initializer) {
        initializer.accept(object);
        return object;
    }

    public static String formatUuid(String uuidStr) {
        int len = uuidStr.length();
        if (len == 36) return uuidStr;
        if (len != 32) throw new IllegalArgumentException("Invalid UUID string: " + uuidStr);
        return uuidStr.substring(0, 8) + '-' +
               uuidStr.substring(8, 12) + '-' +
               uuidStr.substring(12, 16) + '-' +
               uuidStr.substring(16, 20) + '-' +
               uuidStr.substring(20);
    }

    public static UUID parseUuid(String uuidStr) {
        return UUID.fromString(formatUuid(uuidStr));
    }

    public static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[]{
            (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
            (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>> 8),  (byte) msb,
            (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
            (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>> 8),  (byte) lsb
        };
    }

}
