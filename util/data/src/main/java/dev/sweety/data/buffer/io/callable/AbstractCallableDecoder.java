package dev.sweety.data.buffer.io.callable;

import dev.sweety.data.buffer.BufferReader;

import java.util.function.Function;

@FunctionalInterface
public interface AbstractCallableDecoder<T> extends Function<BufferReader, T> {

    T read(BufferReader buffer);

    @Override
    default T apply(BufferReader buffer) {
        return read(buffer);
    }
}
