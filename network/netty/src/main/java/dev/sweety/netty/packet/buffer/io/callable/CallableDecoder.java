package dev.sweety.netty.packet.buffer.io.callable;

import dev.sweety.netty.packet.buffer.PacketBuffer;

import java.util.function.Function;

@FunctionalInterface
public interface CallableDecoder<T> extends Function<PacketBuffer, T> {

    T read(final PacketBuffer buffer);

    @Override
    default T apply(final PacketBuffer buffer){
        return read(buffer);
    }
}
