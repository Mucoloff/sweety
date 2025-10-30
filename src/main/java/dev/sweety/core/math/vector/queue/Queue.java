package dev.sweety.core.math.vector.queue;

public interface Queue<E> {
    void enqueue(E e);
    E dequeue();
    E peek();
    boolean isEmpty();

    int size();

    void clear();
}
