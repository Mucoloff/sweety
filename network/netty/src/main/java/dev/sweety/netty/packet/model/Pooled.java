package dev.sweety.netty.packet.model;

import dev.sweety.netty.packet.buffer.io.Decoder;
import io.netty.util.Recycler;

public interface Pooled<T extends Pooled<T>> extends Decoder {

    Recycler.Handle<T> handle();

    void reset();

    default void tryRecycle() {
        final Recycler.Handle<T> h = handle();
        if (h != null) {
            reset();
            //noinspection unchecked
            h.recycle((T) this);
        }
    }
}
