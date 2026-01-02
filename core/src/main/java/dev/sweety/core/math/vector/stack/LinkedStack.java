package dev.sweety.core.math.vector.stack;

import java.util.Objects;

public class LinkedStack<F> implements Stack<F> {
    private Node<F> top;

    @Override
    public void push(F frame) {
        top = new Node<>(Objects.requireNonNull(frame), top);
    }

    @Override
    public F peek() {
        return top != null ? top.value() : null;
    }

    @Override
    public F pop() {
        if (top == null) return null;
        F f = top.value();
        top = top.next();
        return f;
    }

    @Override
    public boolean isEmpty() {
        return top == null;
    }

    @Override
    public void clear() {
        top = null;
    }
}
