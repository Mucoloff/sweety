package dev.sweety.data.buffer.io.callable;

import dev.sweety.data.buffer.BufferWriter;

@FunctionalInterface
public interface AbstractCallableEncoder<T> {

    void write(BufferWriter buffer, T data);

    default void accept(T data, BufferWriter buffer) {
        write(buffer, data);
    }
}
