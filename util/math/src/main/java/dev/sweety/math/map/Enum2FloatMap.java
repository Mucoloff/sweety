package dev.sweety.math.map;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fastutil-style primitive enum map: {@code float} values in an ordinal-indexed array, no boxing on
 * the primitive put/get/remove path. Also implements {@link Map} (like fastutil's own maps) so it
 * drops into any JDK API expecting one — boxing only happens at that interface boundary. Presence is
 * tracked in a bitset so a stored {@code 0} is still distinguishable from an absent key. {@link #keySet()}/
 * {@link #values()}/{@link #entrySet()} are read-only snapshots, not live views.
 */
public final class Enum2FloatMap<E extends Enum<E>> implements Map<E, Float> {

    /** The JDK has no {@code ObjFloatConsumer} — this fills the gap for {@link #forEachFloat}. */
    @FunctionalInterface
    public interface ObjFloatConsumer<E> {
        void accept(E key, float value);
    }

    private final Class<E> keyType;
    private final E[] universe;
    private final float[] values;
    private final long[] present;
    private int size;

    private Enum2FloatMap(Class<E> keyType) {
        this.keyType = keyType;
        this.universe = keyType.getEnumConstants();
        this.values = new float[universe.length];
        this.present = new long[(universe.length + 63) >> 6];
    }

    public static <E extends Enum<E>> Enum2FloatMap<E> of(Class<E> keyType) {
        return new Enum2FloatMap<>(keyType);
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
    public float put(E key, float value) {
        int ordinal = key.ordinal();
        float previous = values[ordinal];
        if (!isPresent(ordinal)) {
            markPresent(ordinal);
            size++;
        }
        values[ordinal] = value;
        return previous;
    }

    /** @return the value, or {@code 0} if the key is absent. */
    public float get(E key) {
        return values[key.ordinal()];
    }

    public float getOrDefault(E key, float defaultValue) {
        int ordinal = key.ordinal();
        return isPresent(ordinal) ? values[ordinal] : defaultValue;
    }

    public boolean containsKey(E key) {
        return isPresent(key.ordinal());
    }

    /** @return the removed value, or {@code 0} if the key was absent. */
    public float remove(E key) {
        int ordinal = key.ordinal();
        if (!isPresent(ordinal)) return 0;
        markAbsent(ordinal);
        size--;
        float previous = values[ordinal];
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
        Arrays.fill(values, 0f);
        Arrays.fill(present, 0L);
        size = 0;
    }

    /** Iterates present entries in enum order. */
    public void forEachFloat(ObjFloatConsumer<E> action) {
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
        if (!(value instanceof Float target)) return false;
        for (E key : universe) {
            if (isPresent(key.ordinal()) && values[key.ordinal()] == target) return true;
        }
        return false;
    }

    @Override
    public Float get(Object key) {
        if (!keyType.isInstance(key)) return null;
        int ordinal = ((E) key).ordinal();
        return isPresent(ordinal) ? values[ordinal] : null;
    }

    /** @return the previous boxed value, or {@code null} if the key was absent. */
    @Override
    public Float put(E key, Float value) {
        Objects.requireNonNull(value, "value");
        int ordinal = key.ordinal();
        Float previous = isPresent(ordinal) ? values[ordinal] : null;
        if (!isPresent(ordinal)) {
            markPresent(ordinal);
            size++;
        }
        values[ordinal] = value;
        return previous;
    }

    /** @return the removed boxed value, or {@code null} if the key was absent. */
    @Override
    public Float remove(Object key) {
        if (!keyType.isInstance(key)) return null;
        int ordinal = ((E) key).ordinal();
        if (!isPresent(ordinal)) return null;
        markAbsent(ordinal);
        size--;
        float previous = values[ordinal];
        values[ordinal] = 0;
        return previous;
    }

    @Override
    public void putAll(Map<? extends E, ? extends Float> m) {
        for (Map.Entry<? extends E, ? extends Float> e : m.entrySet()) put(e.getKey(), e.getValue());
    }

    private EnumMap<E, Float> snapshot() {
        EnumMap<E, Float> out = new EnumMap<>(keyType);
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
    public Collection<Float> values() {
        return Collections.unmodifiableCollection(snapshot().values());
    }

    @Override
    public Set<Entry<E, Float>> entrySet() {
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
