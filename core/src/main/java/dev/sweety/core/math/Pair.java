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
            case 0 -> this.first;
            case 1 -> this.second;
            default -> throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
        };
    }

    @Override
    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.first == null && this.second == null;
    }

    @Override
    public boolean contains(Object o) {
        return this.first.equals(o) || this.second.equals(o);
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return this.index < size();
            }

            @Override
            public T next() {
                if (this.index == 0) {
                    this.index++;
                    return first;
                } else if (this.index == 1) {
                    this.index++;
                    return second;
                }
                throw new IndexOutOfBoundsException("Index: " + this.index + ", Size: 2");
            }
        };
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        return new Object[]{this.first, this.second};
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T1> T1 @NotNull [] toArray(@NotNull T1 @NotNull [] a) {
        if (a.length < size()) return (T1[]) new Object[]{this.first, this.second};
        a[0] = (T1) this.first;
        a[1] = (T1) this.second;
        return a;
    }

    @Override
    public boolean add(T t) {
        if (this.first == null) {
            this.first = t;
            return true;
        } else if (this.second == null) {
            this.second = t;
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        if (this.first.equals(o)) {
            this.first = null;
            this.size--;
            return true;
        } else if (this.second.equals(o)) {
            this.second = null;
            this.size--;
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return c.stream().allMatch(this::contains);
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
        for (Object o : c) if (remove(o)) modified = true;
        return modified;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        boolean modified = false;
        if (!c.contains(this.first)) {
            this.first = null;
            this.size--;
            modified = true;
        }
        if (!c.contains(this.second)) {
            this.second = null;
            this.size--;
            modified = true;
        }
        return modified;
    }

    @Override
    public void clear() {
        this.first = null;
        this.second = null;
        this.size = 0;
    }

    public int indexOf(T t) {
        if (this.first.equals(t)) return 0;
        if (this.second.equals(t)) return 1;
        return -1;
    }

    public void set(int index, T t) {
        if (index == 0) this.first = t;
        else if (index == 1) this.second = t;
        else throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
        if (t == null) this.size--;
        else this.size++;
    }
}