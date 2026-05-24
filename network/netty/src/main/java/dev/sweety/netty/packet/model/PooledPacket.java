package dev.sweety.netty.packet.model;

import dev.sweety.netty.packet.buffer.io.Decoder;
import io.netty.util.Recycler;

/**
 * Abstract base for pooled packets. Subclasses declare a static {@code Recycler<T>}
 * and an {@code acquire(...)} factory; pooling boilerplate lives here.
 *
 * <pre>{@code
 * private static final Recycler<MyPacket> POOL = new Recycler<>() {
 *     protected MyPacket newObject(Handle<MyPacket> h) { return new MyPacket(h); }
 * };
 * public static MyPacket acquire(int id, long ts, byte[] data) {
 *     MyPacket p = POOL.get();
 *     p.reinitPacket(id, ts, data);
 *     // ... parse fields ...
 *     return p;
 * }
 * }</pre>
 */
public abstract class PooledPacket<T extends PooledPacket<T>> extends Packet implements Decoder {

    private final Recycler.Handle<T> handle;

    protected PooledPacket(Recycler.Handle<T> handle) {
        this.handle = handle;
    }

    @Override
    public void tryRecycle() {
        reset();
        release();
        //noinspection unchecked
        handle.recycle(((T) this));
    }

    /** Clear all mutable fields so the next acquisition sees a fresh instance. */
    protected abstract void reset();
}
