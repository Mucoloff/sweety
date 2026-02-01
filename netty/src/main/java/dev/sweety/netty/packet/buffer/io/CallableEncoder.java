package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface CallableEncoder<T> extends BiConsumer<T, PacketBuffer> {

    void write(final PacketBuffer buffer, final T data);

    @Override
    default void accept(final T data, final PacketBuffer buffer) {
        write(buffer, data);
    }
}
