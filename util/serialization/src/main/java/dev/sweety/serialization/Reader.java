package dev.sweety.serialization;

@FunctionalInterface
public interface Reader<T, D> {
    T read(D source);
}
