package dev.sweety.data.buffer.io.callable;

import dev.sweety.data.buffer.AbstractBuffer;

import java.util.function.Function;

@FunctionalInterface
public interface AbstractCallableDecoder<T, K extends AbstractBuffer<K>> extends Function<K, T> {

    T read(final K buffer);

    @Override
    default T apply(final K buffer){
        return read(buffer);
    }
}
