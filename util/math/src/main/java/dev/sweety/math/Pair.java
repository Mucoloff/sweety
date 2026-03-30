package dev.sweety.math;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Pair<T> implements Collection<T> {

    // Using Object array internally is safer for generic collections
    @SuppressWarnings("unchecked")
    private final T[] data = (T[]) new Object[2];
    private int size = 0;

    public Pair() {

    }

    public Pair(@Nullable T first, @Nullable T second) {
        if (first != null) add(first);
        if (second != null) add(second);
    }

    public T get(int index) {
        if (index != 0 && index != 1) throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
        return this.data[index];
    }

    /**
     * Set element at index. Adjusts size automatically based on null -> value or value -> null transitions.
     */
    public void set(int index, @Nullable T t) {
        if (index != 0 && index != 1) throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");

        final T old = this.data[index];
        if (Objects.equals(old, t)) return; // No change

        this.data[index] = t;

        // Update size consistency
        // If we are here, old != t.
        if (old == null) {
            // old is null, so t must be non-null (otherwise equals would be true)
            this.size++;
        } else if (t == null) {
            // old is non-null, t is null
            this.size--;
        }
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        return this.size() == 0;
    }

    @Override
    public boolean contains(@Nullable Object o) {
        if (o == null) return false; // Used as empty slot indicator
        return Objects.equals(this.data[0], o) || Objects.equals(this.data[1], o);
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return new Iterator<>() {
            private int cursor = 0; // Index of next element to return
            private int lastRet = -1; // Index of last element returned

            @Override
            public boolean hasNext() {
                // Skip nulls (empty slots) to find next valid element
                int i = cursor;
                while (i < 2 && data[i] == null) {
                    i++;
                }
                return i < 2;
            }

            @Override
            public T next() {
                int i = cursor;
                while (i < 2 && data[i] == null) {
                    i++;
                }
                if (i >= 2) throw new NoSuchElementException();

                cursor = i + 1;
                lastRet = i;
                return data[i];
            }

            @Override
            public void remove() {
                if (lastRet < 0) throw new IllegalStateException();
                Pair.this.set(lastRet, null); // Uses set() to maintain size consistency
                lastRet = -1;
            }
        };
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        List<Object> list = new ArrayList<>(size);
        if (data[0] != null) list.add(data[0]);
        if (data[1] != null) list.add(data[1]);
        return list.toArray();
    }

    @Override
    public @NotNull <T1> T1 @NotNull [] toArray(@NotNull T1 @NotNull [] a) {
        List<T> list = new ArrayList<>(size);
        if (data[0] != null) list.add(data[0]);
        if (data[1] != null) list.add(data[1]);
        return list.toArray(a);
    }

    @Override
    public boolean add(@Nullable T t) {
        if (t == null) return false; // Nulls not suggested in this implementation (used as empty)
        if (contains(t)) return false; // Avoid duplicates

        if (this.data[0] == null) {
            set(0, t);
            return true;
        } else if (this.data[1] == null) {
            set(1, t);
            return true;
        }

        // Collection full
        return false; // Or throw IllegalStateException if strict capacity required
    }

    @Override
    public boolean remove(@Nullable Object o) {
        if (o == null) return false;

        if (Objects.equals(this.data[0], o)) {
            set(0, null);
            return true;
        } else if (Objects.equals(this.data[1], o)) {
            set(1, null);
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        for (Object e : c) {
            if (!contains(e)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        boolean modified = false;
        for (T t : c) if (add(t)) modified = true;
        return modified;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        boolean modified = false;
        // Check contains before remove isn't strictly necessary but optimizing is nice
        // Since max size is 2, simple attempt is fine
        for (Object o : c) if (remove(o)) modified = true;
        return modified;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        boolean modified = false;
        // Check data[0]
        if (this.data[0] != null && !c.contains(this.data[0])) {
            set(0, null);
            modified = true;
        }
        // Check data[1]
        if (this.data[1] != null && !c.contains(this.data[1])) {
            set(1, null);
            modified = true;
        }
        return modified;
    }

    @Override
    public void clear() {
        set(0, null);
        set(1, null);
    }

    public int indexOf(@Nullable Object t) {
        if (t == null) return -1;
        if (Objects.equals(this.data[0], t)) return 0;
        if (Objects.equals(this.data[1], t)) return 1;
        return -1;
    }
}