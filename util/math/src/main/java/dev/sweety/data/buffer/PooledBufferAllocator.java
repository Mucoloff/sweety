package dev.sweety.data.buffer;

import dev.sweety.math.pool.Acquire;
import dev.sweety.math.pool.ObjectPool;

import java.util.function.Consumer;

/**
 * Thread-local object pool for buffer allocators, backed by {@link ObjectPool#threadLocal}.
 *
 * <p>Subclasses implement {@link #create} to supply fresh instances and optionally
 * {@link #onDiscard} to clean up surplus buffers (e.g. close off-heap arenas).
 */
public abstract class PooledBufferAllocator<B extends AbstractBuffer<B>> {

    protected PooledBufferAllocator() {
        // `this::recycle` captures `this`, not `pool` — pool is read by recycle() at call time,
        // after field assignment completes. Avoids the javac "self-reference in initializer" error.
    }

    static final int MAX_PER_THREAD = 32;

    private final ObjectPool<B> pool= ObjectPool.threadLocal(() -> create(256, this::recycle))
            .reset(AbstractBuffer::poolReset)
            .onDiscard(this::onDiscard)
            .maxSize(MAX_PER_THREAD)
            .build();

    @Acquire
    public B buffer(int initialCapacity) {
        B buf = pool.acquire();
        if (buf.capacity() < initialCapacity)
            buf.ensureWritable(initialCapacity - buf.capacity());
        return buf;
    }

    protected abstract B create(int cap, Consumer<B> recycler);

    protected void onDiscard(B buf) {}

    private void recycle(B buf) {
        pool.release(buf);
    }
}
