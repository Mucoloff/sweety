package dev.sweety.math.pool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ArrayPoolTest {

    // ========================= THREAD-LOCAL =========================

    @Test
    void threadLocal_acquire_creates_when_empty() {
        ArrayPool<byte[]> pool = ArrayPool.threadLocalBytes(64, 8);
        byte[] arr = pool.acquire(64);
        assertNotNull(arr);
        assertTrue(arr.length >= 64);
    }

    @Test
    void threadLocal_release_and_reacquire_same_instance() {
        ArrayPool<byte[]> pool = ArrayPool.threadLocalBytes(64, 8);
        byte[] a = pool.acquire(64);
        pool.release(a);
        assertSame(a, pool.acquire(64));
    }

    @Test
    void threadLocal_too_small_in_pool_allocates_fresh() {
        ArrayPool<byte[]> pool = ArrayPool.threadLocalBytes(128, 8);
        byte[] small = new byte[32]; // too small: < defaultSize/2=64
        pool.release(small); // rejected: out of [64, 256] range — not pooled

        byte[] acquired = pool.acquire(128);
        assertNotSame(small, acquired, "out-of-range array must not be stored in pool");
        assertTrue(acquired.length >= 128);
    }

    @Test
    void threadLocal_request_larger_than_pooled_allocates_fresh() {
        ArrayPool<byte[]> pool = ArrayPool.threadLocalBytes(64, 8);
        byte[] a = pool.acquire(64);
        pool.release(a);

        byte[] b = pool.acquire(512); // larger than pooled
        assertNotSame(a, b, "pooled array too small for request — must allocate fresh");
        assertTrue(b.length >= 512);
    }

    @Test
    void threadLocal_caps_at_maxPerThread() {
        int max = 3;
        AtomicInteger discards = new AtomicInteger();
        ArrayPool<byte[]> pool = ArrayPool.threadLocal(
                byte[]::new, a -> a.length, 64, arr -> discards.incrementAndGet(), max);

        List<byte[]> held = new ArrayList<>();
        for (int i = 0; i < max + 2; i++) held.add(pool.acquire(64));
        for (byte[] arr : held) pool.release(arr);

        assertEquals(2, discards.get(), "surplus beyond maxPerThread must trigger onDiscard");
    }

    @Test
    void threadLocal_onDiscard_called_for_out_of_range() {
        AtomicInteger discards = new AtomicInteger();
        ArrayPool<byte[]> pool = ArrayPool.threadLocal(
                byte[]::new, a -> a.length, 64, arr -> discards.incrementAndGet(), 8);

        pool.release(new byte[4]);   // too small
        pool.release(new byte[512]); // too large (> 64*2)
        assertEquals(2, discards.get());
    }

    @Test
    void threadLocal_thread_isolation() throws InterruptedException {
        ArrayPool<byte[]> pool = ArrayPool.threadLocalBytes(64, 8);
        byte[] mainArr = pool.acquire(64);
        pool.release(mainArr);

        AtomicReference<byte[]> otherArr = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        new Thread(() -> {
            byte[] arr = pool.acquire(64);
            pool.release(arr);
            otherArr.set(arr);
            done.countDown();
        }).start();
        done.await();

        assertNotSame(mainArr, otherArr.get(), "ThreadLocal: different threads get different instances");
    }

    @Test
    void threadLocal_typed_factories() {
        assertNotNull(ArrayPool.threadLocalInts(16, 4).acquire(16));
        assertNotNull(ArrayPool.threadLocalLongs(16, 4).acquire(16));
        assertNotNull(ArrayPool.threadLocalFloats(16, 4).acquire(16));
        assertNotNull(ArrayPool.threadLocalDoubles(16, 4).acquire(16));
    }

    // ========================= SHARED =========================

    @Test
    void shared_acquire_creates_when_empty() {
        ArrayPool<byte[]> pool = ArrayPool.sharedBytes(64, 8);
        byte[] arr = pool.acquire(64);
        assertNotNull(arr);
        assertTrue(arr.length >= 64);
    }

    @Test
    void shared_release_and_reacquire() {
        ArrayPool<byte[]> pool = ArrayPool.sharedBytes(64, 8);
        byte[] a = pool.acquire(64);
        pool.release(a);
        assertSame(a, pool.acquire(64));
    }

    @Test
    void shared_no_toctou_undersized() {
        // SharedImpl uses pollFirst without peek — verify it never returns undersized
        ArrayPool<byte[]> pool = ArrayPool.sharedBytes(64, 32);
        for (int i = 0; i < 100; i++) {
            byte[] arr = pool.acquire(64);
            assertTrue(arr.length >= 64, "acquired array must meet minSize");
            pool.release(arr);
        }
    }

    @Test
    void shared_cross_thread_recycle() throws InterruptedException {
        ArrayPool<byte[]> pool = ArrayPool.sharedBytes(64, 8);
        byte[] arr = pool.acquire(64);

        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> { pool.release(arr); latch.countDown(); }).start();
        latch.await();

        assertSame(arr, pool.acquire(64), "cross-thread release must be visible");
    }

    @Test
    void shared_null_release_ignored() {
        ArrayPool<byte[]> pool = ArrayPool.sharedBytes(64, 8);
        assertDoesNotThrow(() -> pool.release(null));
    }

    @Test
    void shared_typed_factories() {
        assertNotNull(ArrayPool.sharedInts(16, 4).acquire(16));
        assertNotNull(ArrayPool.sharedLongs(16, 4).acquire(16));
        assertNotNull(ArrayPool.sharedFloats(16, 4).acquire(16));
        assertNotNull(ArrayPool.sharedDoubles(16, 4).acquire(16));
    }
}
