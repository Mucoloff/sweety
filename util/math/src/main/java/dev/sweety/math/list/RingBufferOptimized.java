package dev.sweety.math.list;

/**
 * A high-performance, optimized ring buffer implementation.
 * <p>
 * This class is a thin wrapper around {@link RingBufferCore}, providing a streamlined Queue-like API.
 * It is designed for high-frequency producer/consumer scenarios.
 * </p>
 *
 * @param <E> the type of elements in the buffer
 */
public class RingBufferOptimized<E> {
    private final RingBufferCore<E> core;

    /**
     * Constructs a new RingBufferOptimized with the specified capacity.
     * <p>
     * Note: While the underlying core supports any capacity, using a power-of-two capacity
     * will enable slightly faster bitwise index wrapping.
     * </p>
     *
     * @param capacity the maximum number of elements the buffer can hold
     */
    public RingBufferOptimized(int capacity) {
        this.core = new RingBufferCore<>(capacity);
    }

    /**
     * Adds an element to the buffer.
     *
     * @param packet the element to add
     * @return true if the element was added, false if the buffer is full
     */
    public boolean add(E packet) {
        return core.offer(packet, false);
    }

    /**
     * Retrieves and removes the head of the buffer.
     *
     * @return the head element, or null if empty
     */
    public E poll() {
        return core.poll();
    }

    /**
     * Retrieves but does not remove the head of the buffer.
     *
     * @return the head element, or null if empty
     */
    public E peek() {
        return core.peek();
    }
}