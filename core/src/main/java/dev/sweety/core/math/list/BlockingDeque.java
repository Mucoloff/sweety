package dev.sweety.core.math.list;

import lombok.experimental.Delegate;

import java.util.concurrent.LinkedBlockingDeque;

public class BlockingDeque<E> implements java.util.concurrent.BlockingDeque<E> {
    @Delegate
    private final LinkedBlockingDeque<E> deque;

    public BlockingDeque() {
        this.deque = new LinkedBlockingDeque<>();
    }

    public BlockingDeque(int capacity) {
        this.deque = new LinkedBlockingDeque<>(capacity);
    }

    public void addFixed(E e) {
        while (!deque.offerLast(e)) {
            deque.pollFirst();
        }
    }
}
