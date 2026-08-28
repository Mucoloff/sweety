package dev.sweety.data.buffer.io.callable;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.serialization.Reader;

import java.util.function.Function;

@FunctionalInterface
public interface AbstractCallableDecoder<T> extends Reader<T, BufferReader>, Function<BufferReader, T> {

    @Override
    T read(BufferReader buffer);

    @Override
    default T apply(BufferReader buffer) {
        return read(buffer);
    }
}
