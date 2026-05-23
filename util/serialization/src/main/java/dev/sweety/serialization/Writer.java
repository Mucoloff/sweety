package dev.sweety.serialization;

@FunctionalInterface
public interface Writer<T, S> {
    void write(S sink, T value);
}
