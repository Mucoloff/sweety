package dev.sweety.core.math.pointer;

public class Ptr<T> {
    private final T[] array;
    private int index;

    public Ptr(T[] array, int index) {
        this.array = array;
        this.index = index;
    }

    // Dereferenziazione: *p
    public T get() {
        return array[index];
    }

    // Assegnazione: *p = val
    public void set(T value) {
        array[index] = value;
    }

    // Operatore ++ (avanza il puntatore)
    public Ptr<T> next() {
        index++;
        return this;
    }

    // Operatore -- (indietro)
    public Ptr<T> prev() {
        index--;
        return this;
    }

    // Copia con offset: p + n
    public Ptr<T> offset(int n) {
        return new Ptr<>(array, index + n);
    }

    @Override
    public String toString() {
        return "&[" + index + "]=" + array[index];
    }
}
