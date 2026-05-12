package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.AbstractBuffer;

public interface AbstractEncoder<T extends AbstractBuffer<T>> {

    void write(final T buffer);

}
