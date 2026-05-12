package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.AbstractBuffer;

public interface AbstractDecoder<T extends AbstractBuffer<T>> {

    void read(final T buffer);

}
