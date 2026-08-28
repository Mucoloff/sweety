package dev.sweety.math.map;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ObjIntConsumer;

/**
 * Fastutil-style primitive enum map: {@code int} values in an ordinal-indexed array, no boxing on the
 * primitive put/get/remove path. Also implements {@link Map} (like fastutil's own maps) so it drops
 * into any JDK API expecting one — boxing only happens at that interface boundary. Presence is
 * tracked in a bitset so a stored {@code 0} is still distinguishable from an absent key. {@link #keySet()}/
 * {@link #values()}/{@link #entrySet()} are read-only snapshots, not live views.
 */
public final class Enum2IntMap<E extends Enum<E>> implements Map<E, Integer> {

    private final Class<E> keyType;
    private final E[] universe;
    private final int[] values;
    private final long[] present;
    private int size;

    private Enum2IntMap(Class<E> keyType) {
        this.keyType = keyType;
        this.universe = keyType.getEnumConstants();
        this.values = new int[universe.length];
        this.present = new long[(universe.length + 63) >> 6];
    }

    public static <E extends Enum<E>> Enum2IntMap<E> of(Class<E> keyType) {
        return new Enum2IntMap<>(keyType);
    }

    public Class<E> keyType() {
        return keyType;
    }

    private boolean isPresent(int ordinal) {
        return (present[ordinal >> 6] & (1L << (ordinal & 63))) != 0;
    }

    private void markPresent(int ordinal) {
        present[ordinal >> 6] |= 1L << (ordinal & 63);
    }

    private void markAbsent(int ordinal) {
        present[ordinal >> 6] &= ~(1L << (ordinal & 63));
    }

    /** @return the previous value, or {@code 0} if the key was absent. */
    public int put(E key, int value) {
        int ordinal = key.ordinal();
        int previous = values[ordinal];
        if (!isPresent(ordinal)) {
            markPresent(ordinal);
            size++;
        }
        values[ordinal] = value;
        return previous;
    }

    /** @return the value, or {@code 0} if the key is absent. */
    public int get(E key) {
        return values[key.ordinal()];
    }

    public int getOrDefault(E key, int defaultValue) {
        int ordinal = key.ordinal();
        return isPresent(ordinal) ? values[ordinal] : defaultValue;
    }

    public boolean containsKey(E key) {
        return isPresent(key.ordinal());
    }

    /** @return the removed value, or {@code 0} if the key was absent. */
    public int remove(E key) {
        int ordinal = key.ordinal();
        if (!isPresent(ordinal)) return 0;
        markAbsent(ordinal);
        size--;
        int previous = values[ordinal];
        values[ordinal] = 0;
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
        Arrays.fill(values, 0);
        Arrays.fill(present, 0L);
        size = 0;
    }

    /** Iterates present entries in enum order. */
    public void forEachInt(ObjIntConsumer<E> action) {
        for (E key : universe) {
            if (isPresent(key.ordinal())) action.accept(key, values[key.ordinal()]);
        }
    }

    // ── java.util.Map interface (boxes only here, not on the primitive fast path) ──

    @Override
    public boolean containsKey(Object key) {
        return keyType.isInstance(key) && isPresent(((E) key).ordinal());
    }

    @Override
    public boolean containsValue(Object value) {
        if (!(value instanceof Integer target)) return false;
        for (E key : universe) {
            if (isPresent(key.ordinal()) && values[key.ordinal()] == target) return true;
        }
        return false;
    }

    @Override
    public Integer get(Object key) {
        if (!keyType.isInstance(key)) return null;
        int ordinal = ((E) key).ordinal();
        return isPresent(ordinal) ? values[ordinal] : null;
    }

    /** @return the previous boxed value, or {@code null} if the key was absent. */
    @Override
    public Integer put(E key, Integer value) {
        Objects.requireNonNull(value, "value");
        int ordinal = key.ordinal();
        Integer previous = isPresent(ordinal) ? values[ordinal] : null;
        if (!isPresent(ordinal)) {
            markPresent(ordinal);
            size++;
        }
        values[ordinal] = value;
        return previous;
    }

    /** @return the removed boxed value, or {@code null} if the key was absent. */
    @Override
    public Integer remove(Object key) {
        if (!keyType.isInstance(key)) return null;
        int ordinal = ((E) key).ordinal();
        if (!isPresent(ordinal)) return null;
        markAbsent(ordinal);
        size--;
        int previous = values[ordinal];
        values[ordinal] = 0;
        return previous;
    }

    @Override
    public void putAll(Map<? extends E, ? extends Integer> m) {
        for (Map.Entry<? extends E, ? extends Integer> e : m.entrySet()) put(e.getKey(), e.getValue());
    }

    private EnumMap<E, Integer> snapshot() {
        EnumMap<E, Integer> out = new EnumMap<>(keyType);
        for (E key : universe) {
            if (isPresent(key.ordinal())) out.put(key, values[key.ordinal()]);
        }
        return out;
    }

    @Override
    public Set<E> keySet() {
        return Collections.unmodifiableSet(snapshot().keySet());
    }

    @Override
    public Collection<Integer> values() {
        return Collections.unmodifiableCollection(snapshot().values());
    }

    @Override
    public Set<Entry<E, Integer>> entrySet() {
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
