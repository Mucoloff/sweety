package dev.sweety.core.math;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pair<T> implements Collection<T> {

    private T first, second;
    private int size = 0;

    public T get(int index) {
        return switch (index) {
            case 0 -> first;
            case 1 -> second;
            default -> throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
        };
    }

    @Override
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return first == null && second == null;
    }

    @Override
    public boolean contains(Object o) {
        return first.equals(o) || second.equals(o);
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size();
            }

            @Override
            public T next() {
                if (index == 0) {
                    index++;
                    return first;
                } else if (index == 1) {
                    index++;
                    return second;
                }
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
            }
        };
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        return new Object[]{first, second};
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T1> T1 @NotNull [] toArray(@NotNull T1 @NotNull [] a) {
        if (a.length < size()) return (T1[]) new Object[]{first, second};
        a[0] = (T1) first;
        a[1] = (T1) second;
        return a;
    }

    @Override
    public boolean add(T t) {
        if (first == null) {
            first = t;
            return true;
        } else if (second == null) {
            second = t;
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        if (first.equals(o)) {
            first = null;
            size--;
            return true;
        } else if (second.equals(o)) {
            second = null;
            size--;
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        boolean modified = false;
        for (T t : c) {
            if (add(t)) modified = true;
        }
        return modified;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            if (remove(o)) modified = true;
        }
        return modified;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        boolean modified = false;
        if (!c.contains(first)) {
            first = null;
            size--;
            modified = true;
        }
        if (!c.contains(second)) {
            second = null;
            size--;
            modified = true;
        }
        return modified;
    }

    @Override
    public void clear() {
        this.first = null;
        this.second = null;
        size = 0;
    }

    public int indexOf(T t) {
        if (first.equals(t)) return 0;
        if (second.equals(t)) return 1;
        return -1;
    }

    public void set(int index, T t) {
        if (index == 0) first = t;
        else if (index == 1) second = t;
        else throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
        if (t == null) size--;
        else size++;
    }
}