package dev.sweety.filter;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CountMinSketchTest {

    private CountMinSketch sketchDefault;

    private CountMinSketch sketchCustom;
    private HashFunction[] customHashers;
    private AtomicInteger[] customCalls;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        sketchDefault = new CountMinSketch(1024, 5);
        customCalls = new AtomicInteger[5];
        customHashers = buildTrackingHashers(customCalls);
        sketchCustom = new CountMinSketch(1024, customHashers);
    }

    static HashFunction[] buildTrackingHashers(AtomicInteger[] calls) {
        HashFunction[] arr = new HashFunction[calls.length];
        for (int i = 0; i < calls.length; i++) {
            calls[i] = new AtomicInteger();
            final int row = i;
            arr[i] = data -> {
                calls[row].incrementAndGet();
                return row * 1_000_003 ^ java.util.Arrays.hashCode(data);
            };
        }
        return arr;
    }

    @Test
    void testAddAndEstimateWithDefaultHashFunction() {
        byte[] data1 = "test1".getBytes();

        int estimateBefore = sketchDefault.estimate(data1);
        assertEquals(0, estimateBefore);

        sketchDefault.add(data1);
        int estimateAfter = sketchDefault.estimate(data1);
        assertTrue(estimateAfter > 0, "Estimate should be positive after adding");
    }

    @Test
    void testAddAndEstimateWithCustomHashFunctions() {
        byte[] data1 = "test1".getBytes();

        int estimateBefore = sketchCustom.estimate(data1);
        assertEquals(0, estimateBefore);

        sketchCustom.add(data1);
        for (AtomicInteger c : customCalls) {
            assertTrue(c.get() >= 1, "each custom hasher should run on add");
        }

        int estimateAfter = sketchCustom.estimate(data1);
        assertTrue(estimateAfter > 0, "Estimate should be positive after adding");
        for (AtomicInteger c : customCalls) {
            assertTrue(c.get() >= 2, "each hasher used again on estimate");
        }
    }

    @Test
    void testMultipleAdds() {
        byte[] data = "testData".getBytes();

        sketchDefault.add(data);
        int estimate1 = sketchDefault.estimate(data);

        sketchDefault.add(data);
        int estimate2 = sketchDefault.estimate(data);

        assertTrue(estimate2 >= estimate1, "Estimate should increase with more additions");
    }

    @Test
    void testAge() {
        byte[] data = "aging".getBytes();

        sketchDefault.add(data);
        sketchDefault.add(data);
        int estimateBeforeAge = sketchDefault.estimate(data);

        sketchDefault.age();
        int estimateAfterAge = sketchDefault.estimate(data);

        assertTrue(estimateAfterAge < estimateBeforeAge || estimateAfterAge == 0,
                "Estimate should decrease or become zero after aging");
    }

    @Test
    void testDifferentItems() {
        byte[] data1 = "item1".getBytes();
        byte[] data2 = "item2".getBytes();

        sketchDefault.add(data1);
        sketchDefault.add(data1);
        sketchDefault.add(data2);

        int est1 = sketchDefault.estimate(data1);
        int est2 = sketchDefault.estimate(data2);

        assertTrue(est1 >= est2, "Item1 should have higher or equal estimate (added twice vs once)");
    }

    @Test
    void testDepthMatchesHasherCount() {
        assertEquals(5, sketchDefault.hashFunctionCount());
        assertEquals(5, sketchCustom.hashFunctionCount());
    }
}
