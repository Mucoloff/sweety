package dev.sweety.core.math.vector.stack;

public interface Stack<F> {
    void push(F frame);

    F peek();

    F pop();

    F top();

    boolean isEmpty();

    void clear();
}
