package dev.sweety.core.math.vector.deque;

import dev.sweety.core.math.vector.deque.queue.Queue;
import dev.sweety.core.math.vector.deque.stack.Stack;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;

public class BlockingDeque<E> implements Queue<E>, Stack<E> {

    private final LinkedBlockingDeque<E> deque;

    public BlockingDeque() {
        this.deque = new LinkedBlockingDeque<>();
    }

    public BlockingDeque(int capacity) {
        this.deque = new LinkedBlockingDeque<>(capacity);
    }

    /* ================= QUEUE (FIFO) ================= */

    @Override
    public void enqueue(E e) {
        Objects.requireNonNull(e);
        deque.offerLast(e);
    }

    @Override
    public E dequeue() {
        return deque.pollFirst();
    }

    /* ================= STACK (LIFO) ================= */

    @Override
    public void push(E frame) {
        Objects.requireNonNull(frame);
        deque.offerFirst(frame);
    }

    @Override
    public E pop() {
        return deque.pollFirst();
    }

    @Override
    public E top() {
        return deque.peekFirst();
    }

    /* ================= COMMON ================= */

    @Override
    public E peek() {
        return deque.peekFirst();
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

    /* ================= BLOCKING (opzionali) ================= */

    public E take() throws InterruptedException {
        return deque.takeFirst();
    }

    public void put(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        deque.putLast(e);
    }
}
