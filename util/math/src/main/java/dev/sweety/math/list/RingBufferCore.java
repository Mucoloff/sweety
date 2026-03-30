package dev.sweety.math.list;

import java.util.Objects;

/**
 * A reusable, low-level, high-performance circular buffer core.
 * <p>
 * This class serves as the single source of truth for circular buffer logic,
 * supporting both power-of-two (bitmask) and general capacities (optimized branch-wrap).
 * It is designed to be allocation-free in hot paths.
 * </p>
 *
 * @param <E> the type of elements in the buffer
 */
public final class RingBufferCore<E> {

    private final E[] elements;
    private final int capacity;
    /**
     * Bitmask used for index calculation if capacity is a power of two.
     * If capacity is not a power of two, this is -1.
     */
    private final int mask;

    private int head = 0;
    private int size = 0;

    /**
     * Constructs a new RingBufferCore with the specified capacity.
     *
     * @param capacity the maximum number of elements the buffer can hold
     * @throws IllegalArgumentException if capacity is not positive
     */
    public RingBufferCore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        //noinspection unchecked
        this.elements = (E[]) new Object[capacity];
        this.mask = isPowerOfTwo(capacity) ? capacity - 1 : -1;
    }

    private static boolean isPowerOfTwo(int n) {
        return (n > 0) && ((n & (n - 1)) == 0);
    }

    /**
     * Wraps a logical index to a valid array index.
     * Uses bitmasking if mask is available, otherwise uses conditional subtraction.
     */
    private int wrap(int index) {
        if (mask != -1) {
            return index & mask;
        }
        return (index >= capacity) ? (index - capacity) : index;
    }

    /**
     * Offers an element to the buffer.
     *
     * @param e                 the element to add
     * @param overwriteWhenFull if true, overwrites the oldest element when full.
     *                          if false, returns false when full.
     * @return true if the element was added, false if the buffer was full and overwriteWhenFull is false.
     */
    public boolean offer(E e, boolean overwriteWhenFull) {
        if (size == capacity) {
            if (overwriteWhenFull) {
                elements[head] = e;
                head = wrap(head + 1);
                // size remains equal to capacity
                return true;
            }
            return false;
        }

        int tail = wrap(head + size);
        elements[tail] = e;
        size++;
        return true;
    }

    /**
     * Retrieves and removes the head of the buffer.
     *
     * @return the head element, or null if empty
     */
    public E poll() {
        if (size == 0) {
            return null;
        }
        E element = elements[head];
        elements[head] = null;
        head = wrap(head + 1);
        size--;
        return element;
    }

    /**
     * Retrieves but does not remove the head of the buffer.
     *
     * @return the head element, or null if empty
     */
    public E peek() {
        if (size == 0) {
            return null;
        }
        return elements[head];
    }

    /**
     * Gets the element at the specified logical index (relative to head).
     *
     * @param index logical index (0 to size-1)
     * @return the element
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public E get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return elements[wrap(head + index)];
    }

    /**
     * Clears the buffer.
     *
     * @param nullify if true, nulls out references in the array for GC.
     */
    public void clear(boolean nullify) {
        if (nullify && size > 0) {
            for (int i = 0; i < size; i++) {
                elements[wrap(head + i)] = null;
            }
        }
        head = 0;
        size = 0;
    }

    /**
     * Checks if the buffer is full.
     */
    public boolean isFull() {
        return size == capacity;
    }

    /**
     * Returns the current number of elements.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the maximum capacity.
     */
    public int capacity() {
        return capacity;
    }
}

