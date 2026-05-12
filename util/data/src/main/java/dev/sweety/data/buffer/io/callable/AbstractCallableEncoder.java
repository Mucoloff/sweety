package dev.sweety.netty.packet.buffer.io.callable.abs;

import dev.sweety.netty.packet.buffer.AbstractBuffer;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface AbstractCallableEncoder<T, B extends AbstractBuffer<B>> extends BiConsumer<T, B> {

    void write(final B buffer, final T data);

    @Override
    default void accept(final T data, final B buffer) {
        write(buffer, data);
    }
}

