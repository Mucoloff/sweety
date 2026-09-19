package dev.sweety.math.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Primitive2EnumMapTest {

    enum SampleOp {
        ADD, SUB, MUL, DIV, LOAD, STORE, RETURN
    }

    @Test
    @DisplayName("Int2EnumMap handles both dense 0..255 and sparse keys correctly")
    void testInt2EnumMap() {
        Int2EnumMap<SampleOp> map = Int2EnumMap.of(SampleOp.class);
        assertTrue(map.isEmpty());

        // Dense range
        assertNull(map.put(96, SampleOp.ADD));
        assertNull(map.put(100, SampleOp.SUB));
        assertEquals(SampleOp.ADD, map.get(96));
        assertEquals(SampleOp.SUB, map.get(100));
        assertTrue(map.containsKey(96));
        assertFalse(map.containsKey(97));

        // Overwrite
        assertEquals(SampleOp.ADD, map.put(96, SampleOp.MUL));
        assertEquals(SampleOp.MUL, map.get(96));

        // Sparse range (negative and > 255)
        assertNull(map.put(-5, SampleOp.DIV));
        assertNull(map.put(1000, SampleOp.RETURN));
        assertEquals(SampleOp.DIV, map.get(-5));
        assertEquals(SampleOp.RETURN, map.get(1000));
        assertEquals(4, map.size());

        // Remove
        assertEquals(SampleOp.DIV, map.remove(-5));
        assertNull(map.get(-5));
        assertEquals(SampleOp.MUL, map.remove(96));
        assertNull(map.get(96));
        assertEquals(2, map.size());

        // Clear
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    @DisplayName("Byte2EnumMap correctly maps all byte values")
    void testByte2EnumMap() {
        Byte2EnumMap<SampleOp> map = Byte2EnumMap.of(SampleOp.class);
        map.put((byte) 0, SampleOp.ADD);
        map.put((byte) 127, SampleOp.SUB);
        map.put((byte) -128, SampleOp.MUL);

        assertEquals(3, map.size());
        assertEquals(SampleOp.ADD, map.get((byte) 0));
        assertEquals(SampleOp.SUB, map.get((byte) 127));
        assertEquals(SampleOp.MUL, map.get((byte) -128));
        assertNull(map.get((byte) 50));
    }

    @Test
    @DisplayName("Boolean2EnumMap correctly stores true and false states")
    void testBoolean2EnumMap() {
        Boolean2EnumMap<SampleOp> map = Boolean2EnumMap.of(SampleOp.class);
        assertTrue(map.isEmpty());

        map.put(true, SampleOp.RETURN);
        assertEquals(1, map.size());
        assertEquals(SampleOp.RETURN, map.get(true));
        assertNull(map.get(false));

        map.put(false, SampleOp.LOAD);
        assertEquals(2, map.size());
        assertEquals(SampleOp.LOAD, map.get(false));

        map.remove(true);
        assertEquals(1, map.size());
        assertNull(map.get(true));
        assertEquals(SampleOp.LOAD, map.get(false));
    }

    @Test
    @DisplayName("Long2EnumMap and Short2EnumMap work seamlessly")
    void testLongAndShort() {
        Long2EnumMap<SampleOp> lmap = Long2EnumMap.of(SampleOp.class);
        lmap.put(100L, SampleOp.ADD);
        lmap.put(10000000000L, SampleOp.STORE);
        assertEquals(SampleOp.ADD, lmap.get(100L));
        assertEquals(SampleOp.STORE, lmap.get(10000000000L));

        Short2EnumMap<SampleOp> smap = Short2EnumMap.of(SampleOp.class);
        smap.put((short) 50, SampleOp.DIV);
        smap.put((short) 1000, SampleOp.SUB);
        assertEquals(SampleOp.DIV, smap.get((short) 50));
        assertEquals(SampleOp.SUB, smap.get((short) 1000));
    }

    @Test
    @DisplayName("Int2EnumConcurrentMap handles concurrent multi-threaded writes")
    void testConcurrentMap() throws InterruptedException {
        Int2EnumConcurrentMap<SampleOp> cmap = Int2EnumConcurrentMap.of(SampleOp.class, 8);
        int threads = 4;
        int keysPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < keysPerThread; i++) {
                        int key = threadId * keysPerThread + i;
                        cmap.put(key, SampleOp.values()[key % SampleOp.values().length]);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threads * keysPerThread, cmap.size());
        for (int i = 0; i < threads * keysPerThread; i++) {
            assertEquals(SampleOp.values()[i % SampleOp.values().length], cmap.get(i));
        }
    }
}
