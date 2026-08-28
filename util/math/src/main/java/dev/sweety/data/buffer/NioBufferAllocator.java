package dev.sweety.data.buffer;

import dev.sweety.math.pool.Acquire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

/**
 * Factory for {@link NioBuffer} allocation.
 *
 * <p>Well-known instances:
 * <ul>
 *   <li>{@link #POOLED}  — thread-local pool of direct buffers; mirrors {@link SegmentBufferAllocator#POOLED} semantics.</li>
 *   <li>{@link #HEAP}    — unpooled heap buffer; lowest overhead for short-lived single-thread use.</li>
 *   <li>{@link #DIRECT}  — unpooled direct buffer; off-heap, no GC pressure on body.</li>
 * </ul>
 */
public interface NioBufferAllocator {

    @Acquire
    NioBuffer buffer(int initialCapacity);

    @Acquire
    default NioBuffer buffer() {
        return buffer(NioBuffer.DEFAULT_CAPACITY);
    }

    /** Unpooled heap buffer. */
    NioBufferAllocator HEAP = NioBuffer::heap;

    /** Unpooled direct buffer. */
    NioBufferAllocator DIRECT = NioBuffer::direct;

    /**
     * Thread-local pool of direct buffers.
     *
     * <p>Same semantics as {@link SegmentBufferAllocator#POOLED}: zero GC pressure on the buffer body,
     * zero lock overhead (thread-local), release must happen on the allocating thread.
     *
     * <p>Pool size per thread: up to {@code 32} buffers. Surplus buffers are discarded (GC Cleaner frees direct memory).
     */
    NioBufferAllocator POOLED = new PooledAllocator();

    /** Default allocator — pooled direct, mirrors Netty's default. */
    NioBufferAllocator DEFAULT = POOLED;

    final class PooledAllocator extends PooledBufferAllocator<NioBuffer> implements NioBufferAllocator {
        @Override
        protected NioBuffer create(int cap, Consumer<NioBuffer> recycler) {
            return new NioBuffer(
                ByteBuffer.allocateDirect(cap).order(ByteOrder.BIG_ENDIAN),
                true,
                recycler
            );
        }
        // onDiscard: no-op — GC Cleaner handles direct ByteBuffer deallocation
    }
}
