package dev.sweety.math.map;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fastutil-style primitive enum map: {@code boolean} values in an ordinal-indexed bitset, no boxing on
 * the primitive put/get/remove path. Also implements {@link Map} (like fastutil's own maps) so it
 * drops into any JDK API expecting one — boxing only happens at that interface boundary. Presence is
 * tracked in its own bitset so an absent key is distinguishable from one explicitly set to
 * {@code false}. {@link #keySet()}/{@link #values()}/{@link #entrySet()} are read-only snapshots, not
 * live views.
 */
public final class Enum2BooleanMap<E extends Enum<E>> implements Map<E, Boolean> {

    /** The JDK has no {@code ObjBooleanConsumer} — this fills the gap for {@link #forEachBoolean}. */
    @FunctionalInterface
    public interface ObjBooleanConsumer<E> {
        void accept(E key, boolean value);
    }

    private final Class<E> keyType;
    private final E[] universe;
    private final long[] values;
    private final long[] present;
    private int size;

    private Enum2BooleanMap(Class<E> keyType) {
        this.keyType = keyType;
        this.universe = keyType.getEnumConstants();
        this.values = new long[(universe.length + 63) >> 6];
        this.present = new long[(universe.length + 63) >> 6];
    }

    public static <E extends Enum<E>> Enum2BooleanMap<E> of(Class<E> keyType) {
        return new Enum2BooleanMap<>(keyType);
    }

    public Class<E> keyType() {
        return keyType;
    }

    private static boolean bit(long[] bits, int ordinal) {
        return (bits[ordinal >> 6] & (1L << (ordinal & 63))) != 0;
    }

    private static void setBit(long[] bits, int ordinal, boolean value) {
        if (value) bits[ordinal >> 6] |= 1L << (ordinal & 63);
        else bits[ordinal >> 6] &= ~(1L << (ordinal & 63));
    }

    /** @return the previous value, or {@code false} if the key was absent. */
    public boolean put(E key, boolean value) {
        int ordinal = key.ordinal();
        boolean previous = bit(values, ordinal);
        if (!bit(present, ordinal)) {
            setBit(present, ordinal, true);
            size++;
        }
        setBit(values, ordinal, value);
        return previous;
    }

    /** @return the value, or {@code false} if the key is absent. */
    public boolean get(E key) {
        return bit(values, key.ordinal());
    }

    public boolean getOrDefault(E key, boolean defaultValue) {
        int ordinal = key.ordinal();
        return bit(present, ordinal) ? bit(values, ordinal) : defaultValue;
    }

    public boolean containsKey(E key) {
        return bit(present, key.ordinal());
    }

    /** @return the removed value, or {@code false} if the key was absent. */
    public boolean remove(E key) {
        int ordinal = key.ordinal();
        if (!bit(present, ordinal)) return false;
        setBit(present, ordinal, false);
        size--;
        boolean previous = bit(values, ordinal);
        setBit(values, ordinal, false);
        return previous;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        Arrays.fill(values, 0L);
        Arrays.fill(present, 0L);
        size = 0;
    }

    /** Iterates present entries in enum order. */
    public void forEachBoolean(ObjBooleanConsumer<E> action) {
        for (E key : universe) {
            if (bit(present, key.ordinal())) action.accept(key, bit(values, key.ordinal()));
        }
    }

    // ── java.util.Map interface (boxes only here, not on the primitive fast path) ──

    @Override
    public boolean containsKey(Object key) {
        return keyType.isInstance(key) && bit(present, ((E) key).ordinal());
    }

    @Override
    public boolean containsValue(Object value) {
        if (!(value instanceof Boolean target)) return false;
        for (E key : universe) {
            if (bit(present, key.ordinal()) && bit(values, key.ordinal()) == target) return true;
        }
        return false;
    }

    @Override
    public Boolean get(Object key) {
        if (!keyType.isInstance(key)) return null;
        int ordinal = ((E) key).ordinal();
        return bit(present, ordinal) ? bit(values, ordinal) : null;
    }

    /** @return the previous boxed value, or {@code null} if the key was absent. */
    @Override
    public Boolean put(E key, Boolean value) {
        Objects.requireNonNull(value, "value");
        int ordinal = key.ordinal();
        Boolean previous = bit(present, ordinal) ? bit(values, ordinal) : null;
        if (!bit(present, ordinal)) {
            setBit(present, ordinal, true);
            size++;
        }
        setBit(values, ordinal, value);
        return previous;
    }

    /** @return the removed boxed value, or {@code null} if the key was absent. */
    @Override
    public Boolean remove(Object key) {
        if (!keyType.isInstance(key)) return null;
        int ordinal = ((E) key).ordinal();
        if (!bit(present, ordinal)) return null;
        setBit(present, ordinal, false);
        size--;
        boolean previous = bit(values, ordinal);
        setBit(values, ordinal, false);
        return previous;
    }

    @Override
    public void putAll(Map<? extends E, ? extends Boolean> m) {
        for (Map.Entry<? extends E, ? extends Boolean> e : m.entrySet()) put(e.getKey(), e.getValue());
    }

    private EnumMap<E, Boolean> snapshot() {
        EnumMap<E, Boolean> out = new EnumMap<>(keyType);
        for (E key : universe) {
            if (bit(present, key.ordinal())) out.put(key, bit(values, key.ordinal()));
        }
        return out;
    }

    @Override
    public Set<E> keySet() {
        return Collections.unmodifiableSet(snapshot().keySet());
    }

    @Override
    public Collection<Boolean> values() {
        return Collections.unmodifiableCollection(snapshot().values());
    }

    @Override
    public Set<Entry<E, Boolean>> entrySet() {
        return Collections.unmodifiableSet(snapshot().entrySet());
    }

    @Override
    public boolean equals(Object o) {
        return snapshot().equals(o);
    }

    @Override
    public int hashCode() {
        return snapshot().hashCode();
    }

    @Override
    public String toString() {
        return snapshot().toString();
    }
}
