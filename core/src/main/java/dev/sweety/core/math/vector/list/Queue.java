package dev.sweety.core.math.vector.list;

public interface Queue<E> {
    boolean enqueue(E e);
    E dequeue();
    E peek();
    boolean isEmpty();

    int size();

    void clear();
}
