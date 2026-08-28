package dev.sweety.data.buffer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferPoolTest {

    private final BufferPool pool = BufferPool.DEFAULT;

    @Test
    void borrowReturnsAtLeastRequestedSize() {
        byte[] arr = pool.borrowBytes(100);
        assertTrue(arr.length >= 100);
        pool.returnBytes(arr);
    }

    @Test
    void borrowedSizeIsNextPowerOf2() {
        byte[] arr = pool.borrowBytes(100);
        assertEquals(128, arr.length); // next pow2 >= 100
        pool.returnBytes(arr);
    }

    @Test
    void returnAndReborrow_sameInstance() {
        byte[] arr = pool.borrowBytes(64);
        pool.returnBytes(arr);
        byte[] arr2 = pool.borrowBytes(64);
        assertSame(arr, arr2, "same thread should get back the same array from pool");
        pool.returnBytes(arr2);
    }

    @Test
    void borrowZero_returnsEmptyArray() {
        byte[] arr = pool.borrowBytes(0);
        assertEquals(0, arr.length);
    }

    @Test
    void borrowExceedingMaxNotPooled() {
        int big = (1 << 20) + 1;
        byte[] arr = pool.borrowBytes(big);
        assertEquals(big, arr.length); // exact, not pooled
        pool.returnBytes(arr); // silently dropped, not pooled
    }

    @Test
    void returnNonPow2_silentlyDropped() {
        byte[] nonPow2 = new byte[100]; // not from pool
        pool.returnBytes(nonPow2);      // should not throw
        byte[] next = pool.borrowBytes(128);
        assertNotSame(nonPow2, next);   // non-pow2 was not pooled
        pool.returnBytes(next);
    }

    @Test
    void bucketOverflowDropsExtra() {
        // borrow/return 9 arrays of the same size — MAX_PER_BUCKET is 8
        byte[][] arrays = new byte[9][];
        for (int i = 0; i < 9; i++) arrays[i] = pool.borrowBytes(256);
        for (byte[] arr : arrays) pool.returnBytes(arr);
        // drain the 8 pooled ones
        for (int i = 0; i < 8; i++) pool.returnBytes(pool.borrowBytes(256));
        // 9th was dropped — fresh allocation
        byte[] fresh = pool.borrowBytes(256);
        assertEquals(256, fresh.length);
        pool.returnBytes(fresh);
    }

    @Test
    void acquireDeflater_returnsResetInstance() {
        Deflater d1 = pool.acquireDeflater();
        d1.setInput(new byte[]{1, 2, 3}, 0, 3);
        d1.finish();
        pool.releaseDeflater(d1);

        Deflater d2 = pool.acquireDeflater();
        assertFalse(d2.finished(), "Deflater must be reset on acquire");
        pool.releaseDeflater(d2);
    }

    @Test
    void acquireInflater_returnsResetInstance() throws DataFormatException {
        byte[] src = "hello pool".getBytes();
        Deflater deflater = pool.acquireDeflater();
        deflater.setInput(src);
        deflater.finish();
        byte[] comp = new byte[64];
        int compLen = deflater.deflate(comp);
        pool.releaseDeflater(deflater);

        Inflater i1 = pool.acquireInflater();
        i1.setInput(comp, 0, compLen);
        byte[] out = new byte[src.length];
        i1.inflate(out, 0, src.length);
        pool.releaseInflater(i1);

        Inflater i2 = pool.acquireInflater();
        // after reset, inflater must be usable for a fresh inflate
        deflater = pool.acquireDeflater();
        deflater.setInput(src);
        deflater.finish();
        compLen = deflater.deflate(comp);
        pool.releaseDeflater(deflater);

        byte[] out2 = new byte[src.length];
        i2.setInput(comp, 0, compLen);
        i2.inflate(out2, 0, src.length);
        assertArrayEquals(src, out2);
        pool.releaseInflater(i2);
    }

    @Test
    void scratchBuckets_backed_by_ArrayPool_roundtrip() {
        // Verify ArrayPool-backed buckets behave exactly as before
        byte[] a64   = pool.borrowBytes(64);   assertEquals(64,   a64.length);
        byte[] a128  = pool.borrowBytes(100);  assertEquals(128,  a128.length);
        byte[] a1024 = pool.borrowBytes(1000); assertEquals(1024, a1024.length);
        pool.returnBytes(a64);
        pool.returnBytes(a128);
        pool.returnBytes(a1024);
        assertSame(a64,   pool.borrowBytes(64));
        assertSame(a128,  pool.borrowBytes(100));
        assertSame(a1024, pool.borrowBytes(1000));
        pool.returnBytes(pool.borrowBytes(64));
        pool.returnBytes(pool.borrowBytes(100));
        pool.returnBytes(pool.borrowBytes(1000));
    }

    @Test
    void threadIsolation_differentThreadsGetDifferentArrays() throws InterruptedException {
        byte[] mainArr = pool.borrowBytes(512);
        pool.returnBytes(mainArr);

        AtomicReference<byte[]> otherArr = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            otherArr.set(pool.borrowBytes(512));
            pool.returnBytes(otherArr.get());
            done.countDown();
        });
        t.start();
        done.await();

        assertNotSame(mainArr, otherArr.get(), "ThreadLocal: different threads get different pool instances");
    }
}
