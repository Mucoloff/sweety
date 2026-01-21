package dev.sweety.core.math.vector.deque.queue;

public interface Queue<E> {
    void enqueue(E e);
    E dequeue();
    E peek();
    boolean isEmpty();

    int size();

    void clear();
}
