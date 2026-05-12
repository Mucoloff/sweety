package dev.sweety.netty.packet.buffer.io.callable;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.data.buffer.io.callable.AbstractCallableEncoder;

@FunctionalInterface
public interface CallableEncoder<T> extends AbstractCallableEncoder<T, PacketBuffer> {
}