package dev.sweety.math.list;

import java.util.concurrent.LinkedBlockingDeque;

public class BlockingDeque<E> extends LinkedBlockingDeque<E> {

    public BlockingDeque() {
        super();
    }

    public BlockingDeque(int capacity) {
        super(capacity);
    }

    public void addFixed(E e) {
        while (!offerLast(e)) {
            pollFirst();
        }
    }
}
