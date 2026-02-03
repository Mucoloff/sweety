package dev.sweety.core.math.vector;

public class PacketRingBufferOptimized<E> {
    private final E[] buffer;

    private int head = 0;
    private int tail = 0;
    private int size = 0;

    private final int capacity;

    @SuppressWarnings("unchecked")
    public PacketRingBufferOptimized(int capacity) {
        this.capacity = capacity;
        if (this.capacity % 2 != 0) {
            throw new IllegalArgumentException("[!] Capacity must be a power of 2");
        }
        this.buffer = (E[]) new Object[capacity];
    }

    public boolean add(E packet) {
        if (this.size == this.capacity) return false;
        
        this.buffer[this.tail] = packet;
        this.tail = (this.tail + 1) & (this.capacity - 1);
        this.size++;
        return true;
    }

    public E poll() {
        if (this.size == 0) return null;
        
        E packet = this.buffer[this.head];
        this.buffer[this.head] = null;
        this.head = (this.head + 1) & (this.capacity - 1);
        this.size--;
        return packet;
    }
}