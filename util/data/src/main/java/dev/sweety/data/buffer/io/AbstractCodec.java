package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.AbstractBuffer;

public interface AbstractCodec<T extends AbstractBuffer<T>> extends AbstractEncoder<T>, AbstractDecoder<T> {
}
