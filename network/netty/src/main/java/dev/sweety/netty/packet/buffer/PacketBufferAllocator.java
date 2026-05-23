package dev.sweety.netty.packet.buffer;

import dev.sweety.data.buffer.PooledBufferAllocator;
import dev.sweety.math.pool.Acquire;

import java.util.function.Consumer;

/**
 * Factory for {@link PacketBuffer} allocation.
 *
 * <p>Well-known instances:
 * <ul>
 *   <li>{@link #POOLED} — thread-local wrapper pool backed by Netty's {@code PooledByteBufAllocator}.
 *       Zero GC pressure for both the ByteBuf body and the wrapper object after warmup.
 *       Release must happen on the allocating thread.</li>
 *   <li>{@link #UNPOOLED} — fresh {@code PooledByteBufAllocator} ByteBuf, fresh wrapper object.
 *       Use for one-shot or non-thread-local lifetimes.</li>
 * </ul>
 */
public interface PacketBufferAllocator {

    @Acquire
    PacketBuffer buffer(int initialCapacity);

    @Acquire
    default PacketBuffer buffer() {
        return buffer(PacketBuffer.DEFAULT_CAPACITY);
    }

    PacketBufferAllocator UNPOOLED = PacketBuffer::new;
    PacketBufferAllocator POOLED   = new Pooled();
    PacketBufferAllocator DEFAULT  = POOLED;

    final class Pooled extends PooledBufferAllocator<PacketBuffer> implements PacketBufferAllocator {
        @Override
        protected PacketBuffer create(int cap, Consumer<PacketBuffer> recycler) {
            return new PacketBuffer(cap, recycler);
        }
        // onDiscard: no-op — Netty's PooledByteBufAllocator manages the ByteBuf lifecycle
    }
}
