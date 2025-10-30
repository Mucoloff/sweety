package dev.sweety.core.math;

public final class CycleArray<T> {
    private final T[] values;
    private int index = 0;

    @SafeVarargs
    public CycleArray(T... values) {
        this.values = values;
    }

    public T[] values() {
        return this.values;
    }

    public T cycle() {
        return values[this.index = ((this.index + 1) % values.length)];
    }

    public T current() {
        return values[index % values.length];
    }

    public T set(int index) {
        return values[this.index = (index % values.length)];
    }

}
