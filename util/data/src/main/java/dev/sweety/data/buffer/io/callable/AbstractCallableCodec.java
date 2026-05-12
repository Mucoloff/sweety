package dev.sweety.netty.packet.buffer.io.callable.abs;

import dev.sweety.netty.packet.buffer.AbstractBuffer;

public interface AbstractCallableCodec<T, K extends AbstractBuffer<K>> extends AbstractCallableEncoder<T, K>, AbstractCallableDecoder<T, K> {
}
