package dev.sweety.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastScalableCountingBloomFilterTest {

    private FastScalableCountingBloomFilter bloomFilterDefault;
    private FastScalableCountingBloomFilter bloomFilterCustom;
    private HashFunction[] customHashers;
    private AtomicInteger[] customCalls;

    @BeforeEach
    void setUp() {
        bloomFilterDefault = new FastScalableCountingBloomFilter(1024, 3, 1.5);
        customCalls = new AtomicInteger[3];
        customHashers = CountMinSketchTest.buildTrackingHashers(customCalls);
        bloomFilterCustom = new FastScalableCountingBloomFilter(1024, 1.5, customHashers);
    }

    @Test
    void testAddAndContainsWithDefaultHashFunction() {
        byte[] data1 = "test1".getBytes();
        byte[] data2 = "test2".getBytes();

        assertFalse(bloomFilterDefault.contains(data1));
        bloomFilterDefault.add(data1);
        assertTrue(bloomFilterDefault.contains(data1));

        assertFalse(bloomFilterDefault.contains(data2));
        bloomFilterDefault.add(data2);
        assertTrue(bloomFilterDefault.contains(data2));
    }

    @Test
    void testAddAndContainsWithCustomHashFunctions() {
        byte[] data1 = "test1".getBytes();
        byte[] data2 = "test2".getBytes();

        assertFalse(bloomFilterCustom.contains(data1));

        bloomFilterCustom.add(data1);
        for (AtomicInteger c : customCalls) {
            assertTrue(c.get() >= 1, "each custom hasher should run on add");
        }
        assertTrue(bloomFilterCustom.contains(data1));

        for (AtomicInteger c : customCalls) {
            c.set(0);
        }
        assertFalse(bloomFilterCustom.contains(data2));
        bloomFilterCustom.add(data2);
        assertTrue(bloomFilterCustom.contains(data2));
    }

    @Test
    void testRemove() {
        byte[] data = "testRemove".getBytes();
        bloomFilterDefault.add(data);
        assertTrue(bloomFilterDefault.contains(data));

        bloomFilterDefault.remove(data);
        assertFalse(bloomFilterDefault.contains(data));
    }

    @Test
    void testElementsCounter() {
        assertEquals(0, bloomFilterDefault.elements());
        bloomFilterDefault.add("test1".getBytes());
        assertEquals(1, bloomFilterDefault.elements());

        bloomFilterDefault.add("test2".getBytes());
        assertEquals(2, bloomFilterDefault.elements());

        bloomFilterDefault.remove("test1".getBytes());
        assertEquals(1, bloomFilterDefault.elements());
    }

    @Test
    void testExpansion() {
        FastScalableCountingBloomFilter smallFilter = new FastScalableCountingBloomFilter(64, 3, 1.5);
        int initialSize = smallFilter.bucketCount();

        // Add enough elements to trigger expansion (need to reach 70% load on 64 = ~45 elements)
        for (int i = 0; i < 50; i++) {
            smallFilter.add(("data" + i).getBytes());
        }
        int finalSize = smallFilter.bucketCount();
        assertTrue(finalSize > initialSize, "Filter should have expanded");
    }

    @Test
    void testEstimatedFPP() {
        double fpp = bloomFilterDefault.estimatedFalsePositiveProbability();
        assertTrue(fpp >= 0 && fpp <= 1, "FPP should be between 0 and 1");

        for (int i = 0; i < 50; i++) {
            bloomFilterDefault.add(("element" + i).getBytes());
        }
        double fppAfter = bloomFilterDefault.estimatedFalsePositiveProbability();
        assertTrue(fppAfter > fpp, "FPP should increase with more elements");
    }

    @Test
    void testHashFunctionCount() {
        assertEquals(3, bloomFilterDefault.hashFunctionCount());
        assertEquals(3, bloomFilterCustom.hashFunctionCount());
    }
}
