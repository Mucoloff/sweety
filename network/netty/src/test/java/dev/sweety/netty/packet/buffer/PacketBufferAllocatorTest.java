package dev.sweety.netty.packet.buffer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class PacketBufferAllocatorTest {

    @Test
    void pooled_buffer_is_writable() {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        buf.writeVarInt(42);
        assertEquals(42, buf.readVarInt());
        buf.release();
    }

    @Test
    void release_and_reacquire_same_thread() {
        PacketBuffer first = PacketBufferAllocator.DEFAULT.buffer();
        first.writeVarInt(1);
        first.release();

        PacketBuffer second = PacketBufferAllocator.DEFAULT.buffer();
        assertSame(first, second, "same-thread recycle should return the same wrapper");
        second.release();
    }

    @Test
    void poolReset_clears_content() {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        buf.writeVarInt(999).writeString("hello");
        buf.release();

        PacketBuffer recycled = PacketBufferAllocator.DEFAULT.buffer();
        assertFalse(recycled.isReadable(), "recycled buffer must be cleared");
        recycled.release();
    }

    @Test
    void buffer_growable() {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer(4);
        for (int i = 0; i < 100; i++) buf.writeVarInt(i);
        for (int i = 0; i < 100; i++) assertEquals(i, buf.readVarInt());
        buf.release();
    }

    @Test
    void thread_isolation() throws InterruptedException {
        PacketBuffer mainBuf = PacketBufferAllocator.DEFAULT.buffer();
        mainBuf.release();

        AtomicReference<PacketBuffer> otherBuf = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
            buf.release();
            otherBuf.set(buf);
            done.countDown();
        });
        t.start();
        done.await();

        assertNotSame(mainBuf, otherBuf.get(), "ThreadLocal: different threads get different instances");
    }

    @Test
    void unpooled_always_fresh() {
        PacketBuffer a = PacketBufferAllocator.UNPOOLED.buffer();
        a.release();
        PacketBuffer b = PacketBufferAllocator.UNPOOLED.buffer();
        assertNotSame(a, b);
        b.release();
    }
}
