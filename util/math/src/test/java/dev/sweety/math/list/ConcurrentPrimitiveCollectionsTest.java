package dev.sweety.math.list;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentPrimitiveCollectionsTest {

    @Test
    void testObject2IntConcurrentOpenHashMap() throws InterruptedException {
        Object2IntConcurrentOpenHashMap<String> map = Object2IntConcurrentOpenHashMap.create();
        assertEquals(-1, map.getInt("missing"));

        map.put("a", 10);
        map.put("b", 20);
        assertEquals(10, map.getInt("a"));
        assertEquals(20, map.getInt("b"));
        assertEquals(2, map.size());

        // Concurrency test
        int threads = 8;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        map.put("t" + threadId + "_" + i, i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(2 + (threads * perThread), map.size());
        assertEquals(10, map.removeInt("a"));
        assertEquals(-1, map.getInt("a"));
    }

    @Test
    void testInt2DoubleConcurrentOpenHashMap() {
        Int2DoubleConcurrentOpenHashMap map = Int2DoubleConcurrentOpenHashMap.create();
        assertEquals(0.0, map.get(42));
        assertEquals(0.5, map.getOrDefault(42, 0.5));

        map.put(1, 10.5);
        map.put(2, 20.25);
        assertEquals(10.5, map.get(1));
        assertEquals(20.25, map.get(2));
        assertEquals(2, map.size());

        double[] sum = new double[1];
        map.forEachEntry((k, v) -> sum[0] += v);
        assertEquals(30.75, sum[0], 0.001);

        assertEquals(10.5, map.remove(1));
        assertEquals(0.0, map.get(1));
    }

    @Test
    void testInt2ObjectConcurrentOpenHashMap() {
        Int2ObjectConcurrentOpenHashMap<String> map = Int2ObjectConcurrentOpenHashMap.create();
        assertNull(map.get(10));

        map.put(10, "val10");
        assertEquals("val10", map.get(10));

        // putIfAbsent
        assertEquals("val10", map.putIfAbsent(10, "newVal"));
        assertEquals("val10", map.get(10));
        assertNull(map.putIfAbsent(20, "val20"));
        assertEquals("val20", map.get(20));

        // computeIfAbsent
        assertEquals("val20", map.computeIfAbsent(20, k -> "computed20"));
        assertEquals("computed30", map.computeIfAbsent(30, k -> "computed" + k));
        assertEquals("computed30", map.get(30));

        // keySet & values
        assertTrue(map.keySet().contains(10));
        assertTrue(map.keySet().contains(20));
        assertTrue(map.keySet().contains(30));
        assertEquals(3, map.values().size());
    }

    @Test
    void testLong2ObjectConcurrentOpenHashMap() {
        Long2ObjectConcurrentOpenHashMap<String> map = Long2ObjectConcurrentOpenHashMap.create();
        assertNull(map.get(100L));

        map.put(100L, "first");
        assertEquals("first", map.get(100L));

        // putIfAbsent & computeIfAbsent
        assertEquals("first", map.putIfAbsent(100L, "other"));
        assertNull(map.putIfAbsent(200L, "second"));
        assertEquals("second", map.get(200L));

        assertEquals("computed300", map.computeIfAbsent(300L, k -> "computed" + k));
        assertEquals("computed300", map.get(300L));

        assertTrue(map.keySet().contains(100L));
        assertTrue(map.keySet().contains(200L));
        assertTrue(map.keySet().contains(300L));
        assertEquals(3, map.values().size());
    }

    @Test
    void testLongConcurrentOpenHashSet() {
        LongConcurrentOpenHashSet set = LongConcurrentOpenHashSet.create();
        assertTrue(set.add(100L));
        assertTrue(set.add(200L));
        assertFalse(set.add(100L));
        assertEquals(2, set.size());

        // removeIf
        assertTrue(set.removeIf(val -> val == 100L));
        assertEquals(1, set.size());
        assertFalse(set.contains(100L));
        assertTrue(set.contains(200L));
    }
}
