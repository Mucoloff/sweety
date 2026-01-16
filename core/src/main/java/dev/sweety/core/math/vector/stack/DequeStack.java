package dev.sweety.core.math.vector.stack;

import dev.sweety.core.math.vector.queue.Queue;

import java.util.ArrayDeque;
import java.util.Objects;

public class DequeStack<F> implements Stack<F>, Queue<F> {
    private final ArrayDeque<F> deque = new ArrayDeque<>();

    @Override
    public void push(F frame) {
        deque.push(Objects.requireNonNull(frame));
    }

    @Override
    public void enqueue(F f) {
        deque.addLast(Objects.requireNonNull(f));
    }

    @Override
    public F dequeue() {
        return deque.pollLast();
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
    public F top() {
        return deque.element();
    }

    @Override
    public boolean isEmpty() {
        return deque.isEmpty();
    }

    @Override
    public int size() {
        return deque.size();
    }

    @Override
    public void clear() {
        deque.clear();
    }
}
