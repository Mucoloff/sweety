package dev.sweety.data.buffer.io.callable;

import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.serialization.Writer;

@FunctionalInterface
public interface AbstractCallableEncoder<T> extends Writer<T, BufferWriter> {

    @Override
    void write(BufferWriter buffer, T data);

    default void accept(T data, BufferWriter buffer) {
        write(buffer, data);
    }
}
