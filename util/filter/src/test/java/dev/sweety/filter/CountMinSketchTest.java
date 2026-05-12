package dev.sweety.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test per CountMinSketch con HashFunction personalizzato
 */
class CountMinSketchTest {

    private CountMinSketch sketchDefault;
    private CountMinSketch sketchCustom;
    private TestHashFunction testHashFunction;

    @BeforeEach
    void setUp() {
        sketchDefault = new CountMinSketch(1024, 5);
        testHashFunction = new TestHashFunction();
        sketchCustom = new CountMinSketch(1024, 5, testHashFunction);
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
    void testAddAndEstimateWithCustomHashFunction() {
        byte[] data1 = "test1".getBytes();

        int estimateBefore = sketchCustom.estimate(data1);
        assertEquals(0, estimateBefore);

        sketchCustom.add(data1);
        assertTrue(testHashFunction.hash1Called);
        assertTrue(testHashFunction.hash2Called);

        int estimateAfter = sketchCustom.estimate(data1);
        assertTrue(estimateAfter > 0, "Estimate should be positive after adding");
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
    void testConstructorWithNullHashFunction() {
        // Deve usare il default MurmurHashFunction
        CountMinSketch sketch = new CountMinSketch(512, 3, null);
        byte[] data = "test".getBytes();

        sketch.add(data);
        assertTrue(sketch.estimate(data) > 0);
    }

    /**
     * HashFunction di test per verificare che viene usato
     */
    static class TestHashFunction implements HashFunction {
        public boolean hash1Called = false;
        public boolean hash2Called = false;
        private int counter = 0;

        @Override
        public int hash1(byte[] data) {
            hash1Called = true;
            return 31 * (counter++) ^ computeHashCode(data);
        }

        @Override
        public int hash2(byte[] data) {
            hash2Called = true;
            return 37 * (counter++) ^ computeHashCode(data);
        }

        void resetCalls() {
            hash1Called = false;
            hash2Called = false;
            counter = 0;
        }

        private int computeHashCode(byte[] data) {
            int hash = 0;
            for (byte b : data) {
                hash = hash * 31 + b;
            }
            return hash;
        }
    }
}

