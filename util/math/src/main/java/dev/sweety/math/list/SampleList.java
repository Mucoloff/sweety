package dev.sweety.math.list;

import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A high-performance circular buffer implementation of {@link java.util.List}.
 * <p>
 * This class uses a reusable {@link RingBufferCore} to store elements. It supports O(1) operations
 * for {@link #add(Object)}, {@link #get(int)}, and {@link #clear()}.
 * </p>
 * <p>
 * <b>Ring Buffer Behavior:</b>
 * When the buffer is full ({@link #size()} == {@link #capacity()}), the behavior of {@link #add(Object)}
 * depends on the {@code overwriteWhenFull} setting:
 * <ul>
 *   <li>If {@code true}: The oldest element is overwritten, and the head of the list advances.</li>
 *   <li>If {@code false}: The new element is rejected, and an {@link IllegalStateException} is thrown.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Thread Safety:</b>
 * This class is <b>NOT</b> thread-safe. If accessed by multiple threads, it must be synchronized externally.
 * </p>
 *
 * @param <T> the type of elements in this list
 */
public final class SampleList<T> extends AbstractList<T> implements RandomAccess {

    private final RingBufferCore<T> core;
    private final boolean overwriteWhenFull;

    /**
     * Constructs a new SampleList with the specified capacity and default rejection behavior.
     *
     * @param capacity the maximum number of elements the list can hold
     */
    public SampleList(int capacity) {
        this(capacity, false);
    }

    /**
     * Constructs a new SampleList with the specified capacity and overwrite policy.
     *
     * @param capacity          the maximum number of elements the list can hold
     * @param overwriteWhenFull if true, the oldest element is overwritten when full; otherwise, an exception is thrown
     */
    public SampleList(int capacity, boolean overwriteWhenFull) {
        this.core = new RingBufferCore<>(capacity);
        this.overwriteWhenFull = overwriteWhenFull;
    }

    /**
     * Constructs a new SampleList initialized with the given array.
     * The list will start full.
     *
     * @param initial           the initial elements
     * @param overwriteWhenFull if true, the oldest element is overwritten when full; otherwise, an exception is thrown
     * @throws NullPointerException if initial is null
     */
    public SampleList(T[] initial, boolean overwriteWhenFull) {
        Objects.requireNonNull(initial, "Initial array must not be null");
        this.core = new RingBufferCore<>(initial.length);
        this.overwriteWhenFull = overwriteWhenFull;
        for (T t : initial) {
            core.offer(t, true);
        }
    }

    @Override
    public boolean add(T t) {
        if (!core.offer(t, overwriteWhenFull)) {
            throw new IllegalStateException("Buffer is full");
        }
        modCount++;
        return true;
    }

    @Override
    public T get(int index) {
        return core.get(index);
    }

    @Override
    public int size() {
        return core.size();
    }

    @Override
    public void clear() {
        clear(true);
    }

    /**
     * Clears the list.
     *
     * @param nullify if true, nulls out references in the underlying array to allow GC.
     *                if false, only resets the size and head, leaving element references.
     */
    public void clear(boolean nullify) {
        core.clear(nullify);
        modCount++;
    }

    /**
     * Resets the list state without clearing element references.
     *
     * @see #clear(boolean)
     */
    public void clearFast() {
        clear(false);
    }

    /**
     * Retrieves and removes the head of this queue, or returns {@code null} if this list is empty.
     *
     * @return the head of this list, or {@code null} if empty
     */
    public T poll() {
        if (core.size() == 0) return null;
        T element = core.poll();
        modCount++;
        return element;
    }

    /**
     * Checks if the list is full.
     */
    public boolean isFull() {
        return core.isFull();
    }

    /**
     * Returns the capacity of this list.
     */
    public int capacity() {
        return core.capacity();
    }

    /**
     * Returns the oldest element in the list (the head), or null if empty.
     */
    public T getOldest() {
        return core.peek();
    }

    /**
     * Returns the newest element in the list (the tail), or null if empty.
     */
    public T getNewest() {
        if (core.size() == 0) return null;
        return core.get(core.size() - 1);
    }
}
