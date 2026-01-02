package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

import java.util.function.Function;

@FunctionalInterface
public interface CallableDecoder<T> extends Function<PacketBuffer, T> {

    T read(PacketBuffer buffer);

    @Override
    default T apply(PacketBuffer buffer){
        return read(buffer);
    }
}
