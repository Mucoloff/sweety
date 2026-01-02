package dev.sweety.cloud.packet.buffer.io;

import dev.sweety.cloud.packet.buffer.PacketBuffer;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface CallableEncoder<T> extends BiConsumer<T, PacketBuffer> {

    void write(PacketBuffer buffer, T data);

    @Override
    default void accept(T data, PacketBuffer buffer){
        write(buffer, data);
    }
}
