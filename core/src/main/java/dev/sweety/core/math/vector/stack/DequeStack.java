package dev.sweety.core.math.vector.stack;

import java.util.ArrayDeque;
import java.util.Objects;

public class DequeStack<F> implements Stack<F> {
    private final ArrayDeque<F> deque = new ArrayDeque<>();

    @Override
    public void push(F frame) {
        deque.push(Objects.requireNonNull(frame));
    }

    @Override
    public F peek() {
        return deque.peek();
    }

    @Override
    public F pop() {
        return deque.poll();
    }

    @Override
    public boolean isEmpty() {
        return deque.isEmpty();
    }

    @Override
    public void clear() {
        deque.clear();
    }
}
