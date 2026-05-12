package dev.sweety.netty.packet.buffer.io.callable;

import dev.sweety.data.buffer.io.callable.AbstractCallableCodec;

public interface CallableCodec<T> extends CallableEncoder<T>, CallableDecoder<T>, AbstractCallableCodec<T> {
}
