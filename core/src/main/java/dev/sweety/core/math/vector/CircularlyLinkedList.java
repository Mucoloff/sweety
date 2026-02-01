package dev.sweety.core.math.vector;

public class CircularlyLinkedList<E> {

    private Node<E> tail = null;
    private int size= 0;

    public CircularlyLinkedList() {}

    /**
     *     LIST
     */

    public E first() {
        if (this.isEmpty()) return null;
        return this.tail.getNext().getElement();
    }

    public E last() {
        if (this.isEmpty()) return null;
        return this.tail.getElement();
    }

    public void rotate() {
        if (this.isEmpty()) return;
        if (this.tail != null) {
            this.tail = this.tail.getNext();
        }
    }

    public void addFirst(E e) {
        if (this.size == 0) {
            this.tail = new Node<>(e, null);
            this.tail.setNext(this.tail);
        }
        else {
            final Node<E> newest = new Node<>(e, this.tail.getNext());
            this.tail.setNext(newest);
        }
        this.size++;
    }

    public void addLast(E e) {
        this.addFirst(e);
        this.tail = this.tail.getNext();
    }

    public E removeFirst() {
        if (this.isEmpty()) return null;
        Node<E> head = this.tail.getNext();

        if (head == this.tail) this.tail = null;
        else this.tail.setNext(head.getNext());

        this.size--;

        return head.getElement();
    }


    /**
     *     STATE
     */

    public int size() {
        return this.size;
    }
    public boolean isEmpty() {
        return this.size == 0;
    }

    public static class Node<E> {
        private E element;
        private Node<E> next;

        public Node(E element, Node<E> next) {
            this.element = element;
            this.next = next;
        }

        public E getElement() {
            return element;
        }

        public void setElement(E element) {
            this.element = element;
        }

        public Node<E> getNext() {
            return next;
        }

        public void setNext(Node<E> next) {
            this.next = next;
        }

        @Override
        public String toString() {
            return String.valueOf(element);
        }
    }
}