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
        if (this.top == null) return null;
        final F f = this.top.value();
        this.top = top.next();
        return f;
    }

    @Override
    public F top() {
        if (this.top == null) return null;
        return top.value();
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
