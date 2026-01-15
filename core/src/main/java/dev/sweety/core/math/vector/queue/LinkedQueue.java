package dev.sweety.core.math.vector.queue;

import java.util.Objects;

/**
 * Thread-safe LinkedQueue FIFO.
 */
public class LinkedQueue<E> implements Queue<E> {

    private Node<E> head;
    private Node<E> tail;
    private int size;

    @Override
    public synchronized void enqueue(E element) {
        Objects.requireNonNull(element);
        Node<E> node = new Node<>(element);
        if (tail != null) tail.next = node;
        else head = node;
        tail = node;
        size++;
        notifyAll();
    }

    @Override
    public synchronized E dequeue() {
        if (head == null) return null;
        final E value = head.value;
        head = head.next;
        if (head == null) tail = null; // coda vuota
        size--;
        return value;
    }

    public synchronized E lockingDequeue() {
        E val;
        while ((val = dequeue()) == null) {
            try {
                wait(); // attende finché non c'è un elemento
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return val;
    }


    public synchronized void enqueueFirst(E element) {
        Objects.requireNonNull(element);
        Node<E> node = new Node<>(element);
        node.next = head;
        head = node;
        if (tail == null) {
            tail = node;
        }
        size++;
        notifyAll();
    }

    @Override
    public synchronized E peek() {
        return head != null ? head.value : null;
    }

    @Override
    public synchronized boolean isEmpty() {
        return head == null;
    }

    @Override
    public synchronized int size() {
        return size;
    }

    @Override
    public synchronized void clear() {
        head = (tail = null);
        size = 0;
    }

    private static final class Node<E> {
        final E value;
        Node<E> next;
        Node(E value) {
            this.value = value;
        }
    }
}
