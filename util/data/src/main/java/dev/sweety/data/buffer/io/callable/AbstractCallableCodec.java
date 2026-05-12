package dev.sweety.data.buffer.io.callable;

import dev.sweety.data.buffer.AbstractBuffer;

public interface AbstractCallableCodec<T, K extends AbstractBuffer<K>> extends AbstractCallableEncoder<T, K>, AbstractCallableDecoder<T, K> {
}
