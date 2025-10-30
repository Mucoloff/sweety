package dev.sweety.core.math.vector.stack;

public interface Stack<F> {
    void push(F frame);

    F peek();

    F pop();

    boolean isEmpty();

    void clear();
}
