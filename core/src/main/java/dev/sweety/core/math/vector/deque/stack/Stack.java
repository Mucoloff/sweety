package dev.sweety.core.math.vector.deque.stack;

public interface Stack<F> {
    void push(F frame);

    F peek();

    F pop();

    F top();

    boolean isEmpty();

    void clear();
}
