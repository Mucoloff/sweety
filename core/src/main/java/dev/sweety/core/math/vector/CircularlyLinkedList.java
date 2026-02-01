package dev.sweety.core.math.vector;

import java.util.*;

public class CircularlyLinkedList<E> implements List<E> {

    private Node<E> tail = null;
    private int size = 0;

    public CircularlyLinkedList() {
    }

    public CircularlyLinkedList(Collection<? extends E> c) {
        addAll(c);
    }

    @SafeVarargs
    public CircularlyLinkedList(E... elements) {
        for (E e : elements) {
            addLast(e);
        }
    }

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

    @Override
    public int size() {
        return this.size;
    }
    
    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (this.isEmpty()) return false;
        Node<E> current = this.tail.getNext();
        for (int i = 0; i < this.size; i++) {
            if (Objects.equals(current.getElement(), o)) {
                return true;
            }
            current = current.getNext();
        }
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private Node<E> current = tail != null ? tail.getNext() : null;
            private int remaining = size;

            @Override
            public boolean hasNext() {
                return remaining > 0;
            }

            @Override
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                E element = current.getElement();
                current = current.getNext();
                remaining--;
                return element;
            }
        };
    }

    @Override
    public Object[] toArray() {
        Object[] array = new Object[this.size];
        if (this.isEmpty()) return array;
        Node<E> current = this.tail.getNext();
        for (int i = 0; i < this.size; i++) {
            array[i] = current.getElement();
            current = current.getNext();
        }
        return array;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < this.size) {
            a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), this.size);
        }
        Object[] result = a;
        Node<E> current = this.tail != null ? this.tail.getNext() : null;
        for (int i = 0; i < this.size; i++) {
            result[i] = current.getElement();
            current = current.getNext();
        }
        if (a.length > this.size) {
            a[this.size] = null;
        }
        return a;
    }

    @Override
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (this.isEmpty()) return false;
        
        Node<E> current = this.tail.getNext();
        Node<E> prev = this.tail;
        
        for (int i = 0; i < this.size; i++) {
            if (Objects.equals(current.getElement(), o)) {
                if (this.size == 1) {
                    this.tail = null;
                } else {
                    prev.setNext(current.getNext());
                    if (current == this.tail) {
                        this.tail = prev;
                    }
                }
                this.size--;
                return true;
            }
            prev = current;
            current = current.getNext();
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            add(e);
            modified = true;
        }
        return modified;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        checkPositionIndex(index);
        if (c.isEmpty()) return false;
        
        for (E e : c) {
            add(index++, e);
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            while (remove(o)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean modified = false;
        Iterator<E> it = iterator();
        List<E> toRemove = new ArrayList<>();
        while (it.hasNext()) {
            E element = it.next();
            if (!c.contains(element)) {
                toRemove.add(element);
            }
        }
        for (E e : toRemove) {
            remove(e);
            modified = true;
        }
        return modified;
    }

    @Override
    public void clear() {
        this.tail = null;
        this.size = 0;
    }

    @Override
    public E get(int index) {
        checkElementIndex(index);
        return getNode(index).getElement();
    }

    @Override
    public E set(int index, E element) {
        checkElementIndex(index);
        Node<E> node = getNode(index);
        E oldValue = node.getElement();
        node.setElement(element);
        return oldValue;
    }

    @Override
    public void add(int index, E element) {
        checkPositionIndex(index);
        if (index == 0) {
            addFirst(element);
        } else if (index == this.size) {
            addLast(element);
        } else {
            Node<E> prev = getNode(index - 1);
            Node<E> newNode = new Node<>(element, prev.getNext());
            prev.setNext(newNode);
            this.size++;
        }
    }

    @Override
    public E remove(int index) {
        checkElementIndex(index);
        if (index == 0) {
            return removeFirst();
        }
        
        Node<E> prev = getNode(index - 1);
        Node<E> toRemove = prev.getNext();
        E element = toRemove.getElement();
        
        prev.setNext(toRemove.getNext());
        if (toRemove == this.tail) {
            this.tail = prev;
        }
        this.size--;
        return element;
    }

    @Override
    public int indexOf(Object o) {
        if (this.isEmpty()) return -1;
        Node<E> current = this.tail.getNext();
        for (int i = 0; i < this.size; i++) {
            if (Objects.equals(current.getElement(), o)) {
                return i;
            }
            current = current.getNext();
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (this.isEmpty()) return -1;
        int lastIndex = -1;
        Node<E> current = this.tail.getNext();
        for (int i = 0; i < this.size; i++) {
            if (Objects.equals(current.getElement(), o)) {
                lastIndex = i;
            }
            current = current.getNext();
        }
        return lastIndex;
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);
        return new ListIterator<E>() {
            private int cursor = index;
            private int lastRet = -1;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                lastRet = cursor;
                return get(cursor++);
            }

            @Override
            public boolean hasPrevious() {
                return cursor > 0;
            }

            @Override
            public E previous() {
                if (!hasPrevious()) throw new NoSuchElementException();
                lastRet = --cursor;
                return get(cursor);
            }

            @Override
            public int nextIndex() {
                return cursor;
            }

            @Override
            public int previousIndex() {
                return cursor - 1;
            }

            @Override
            public void remove() {
                if (lastRet < 0) throw new IllegalStateException();
                CircularlyLinkedList.this.remove(lastRet);
                if (lastRet < cursor) cursor--;
                lastRet = -1;
            }

            @Override
            public void set(E e) {
                if (lastRet < 0) throw new IllegalStateException();
                CircularlyLinkedList.this.set(lastRet, e);
            }

            @Override
            public void add(E e) {
                CircularlyLinkedList.this.add(cursor++, e);
                lastRet = -1;
            }
        };
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        checkPositionIndex(fromIndex);
        checkPositionIndex(toIndex);
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        
        List<E> subList = new ArrayList<>();
        Node<E> current = getNode(fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            subList.add(current.getElement());
            current = current.getNext();
        }
        return subList;
    }

    // Helper methods
    
    private Node<E> getNode(int index) {
        Node<E> current = this.tail.getNext();
        for (int i = 0; i < index; i++) {
            current = current.getNext();
        }
        return current;
    }

    private void checkElementIndex(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + this.size);
        }
    }

    private void checkPositionIndex(int index) {
        if (index < 0 || index > this.size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + this.size);
        }
    }

    @Override
    public String toString() {
        if (this.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        Node<E> current = this.tail.getNext();
        for (int i = 0; i < this.size; i++) {
            sb.append(current.getElement());
            if (i < this.size - 1) sb.append(", ");
            current = current.getNext();
        }
        sb.append("]");
        return sb.toString();
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