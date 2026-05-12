package dev.sweety.netty.packet.buffer.io.callable;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;

@FunctionalInterface
public interface CallableDecoder<T> extends AbstractCallableDecoder<T, PacketBuffer> {
}
