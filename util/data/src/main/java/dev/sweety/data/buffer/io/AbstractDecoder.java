package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.AbstractBuffer;

public interface AbstractDecoder<T extends AbstractBuffer<T>> {

    void read(final T buffer);

}
