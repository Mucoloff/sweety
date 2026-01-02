package dev.sweety.core.math.pointer;

import java.util.Arrays;

public class Ptr<T> {
    private final T[] array;
    private int index = 0;

    @SafeVarargs
    public Ptr(T... values) {
        this.array = values;
    }

    // Dereferenziazione: *p
    public T get() {
        return array[index];
    }

    public T get(int index) {
        return array[this.index = (index % array.length)];
    }

    // Assegnazione: *p = val
    public void set(T value) {
        array[index] = value;
    }

    // Operatore ++ (avanza il puntatore)
    public Ptr<T> next() {
        this.index = ((this.index + 1) % array.length);
        return this;
    }

    // Operatore -- (indietro)
    public Ptr<T> prev() {
        this.index = ((this.index - 1) % array.length);
        return this;
    }

    // Copia con offset: p + n
    public Ptr<T> offset(int n) {
        this.index = ((this.index + n) % array.length);
        return this;
    }

    public T[] values() {
        return this.array;
    }

    @Override
    public String toString() {
        String arr = Arrays.toString(array);
        String curr = get().toString();
        return "%s -> %s".formatted(arr, curr);
    }
}
