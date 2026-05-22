package dev.sweety.data.buffer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Shared thread-local pool logic for buffer allocators.
 *
 * <p>Subclasses implement {@link #create} to supply fresh instances and optionally
 * {@link #onDiscard} to clean up surplus buffers (e.g. close off-heap arenas).
 * Everything else — the ThreadLocal deque, poll/push, poolReset, ensureWritable — lives here.
 */
public abstract class PooledBufferAllocator<B extends AbstractBuffer<B>> {

    static final int MAX_PER_THREAD = 32;

    private final ThreadLocal<Deque<B>> pool = ThreadLocal.withInitial(ArrayDeque::new);

    public B buffer(int initialCapacity) {
        Deque<B> local = pool.get();
        B buf = local.poll();
        if (buf != null) {
            buf.poolReset();
            if (buf.capacity() < initialCapacity)
                buf.ensureWritable(initialCapacity - buf.capacity());
            return buf;
        }
        return create(initialCapacity, this::recycle);
    }

    protected abstract B create(int cap, Consumer<B> recycler);

    protected void onDiscard(B buf) {}

    private void recycle(B buf) {
        Deque<B> local = pool.get();
        if (local.size() < MAX_PER_THREAD) local.push(buf);
        else onDiscard(buf);
    }
}
