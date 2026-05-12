package dev.sweety.netty.packet.buffer.io.callable.abs;

import dev.sweety.netty.packet.buffer.AbstractBuffer;

import java.util.function.Function;

@FunctionalInterface
public interface AbstractCallableDecoder<T, K extends AbstractBuffer<K>> extends Function<K, T> {

    T read(final K buffer);

    @Override
    default T apply(final K buffer){
        return read(buffer);
    }
}
