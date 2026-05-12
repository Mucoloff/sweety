package dev.sweety.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MurmurHasher e {@link HashFunctions#murmur3Defaults}.
 */
class MurmurHasherAndDefaultsTest {

    @Test
    void defaultHashesAreDistinctAcrossRows() {
        HashFunction[] h = HashFunctions.murmur3Defaults(3);
        byte[] data = "testData".getBytes();
        int a = h[0].hash(data);
        int b = h[1].hash(data);
        int c = h[2].hash(data);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }

    @Test
    void murmurHasherChangesWithSeed() {
        MurmurHasher h100 = new MurmurHasher(100);
        MurmurHasher h300 = new MurmurHasher(300);

        byte[] data = "test".getBytes();
        assertNotEquals(h100.hash(data), h300.hash(data));
    }

    @Test
    void determinism() {
        MurmurHasher hasher = new MurmurHasher(0xABCDEF01);
        byte[] data = "consistency".getBytes();

        assertEquals(hasher.hash(data), hasher.hash(data));
    }

    @Test
    void differentInput() {
        MurmurHasher hasher = new MurmurHasher(0);
        byte[] data1 = "data1".getBytes();
        byte[] data2 = "data2".getBytes();

        assertNotEquals(hasher.hash(data1), hasher.hash(data2));
    }

    @Test
    void emptyPayload() {
        MurmurHasher hasher = new MurmurHasher(1);
        assertEquals(hasher.hash(new byte[0]), hasher.hash(new byte[0]));
    }

    @Test
    void copyCollection() {
        HashFunction[] d = HashFunctions.murmur3Defaults(3);
        HashFunction[] copy = HashFunctions.copy(java.util.Arrays.asList(d));
        assertEquals(3, copy.length);
    }

    @Test
    void sketchAndBloomFromCollection() {
        HashFunction[] defaults = HashFunctions.murmur3Defaults(4);
        byte[] key = "k".getBytes();

        CountMinSketch sketch = new CountMinSketch(100, java.util.Arrays.asList(defaults));
        sketch.add(key);
        assertTrue(sketch.estimate(key) >= 1);
        assertEquals(4, sketch.hashFunctionCount());

        FastScalableCountingBloomFilter bloom = new FastScalableCountingBloomFilter(200, 1.25, java.util.Arrays.asList(defaults));
        bloom.add(key);
        assertTrue(bloom.contains(key));
        assertEquals(4, bloom.hashFunctionCount());
    }

    @Test
    void copyCollectionRejectsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> HashFunctions.copy((java.util.Collection<HashFunction>) null));
        assertThrows(IllegalArgumentException.class,
                () -> HashFunctions.copy(java.util.Collections.emptyList()));
    }

    @Test
    void copyRejectsNullArrayOrNullElement() {
        assertThrows(IllegalArgumentException.class,
                () -> HashFunctions.copy((HashFunction[]) null));
        assertThrows(IllegalArgumentException.class, () -> HashFunctions.copy(new HashFunction[0]));
        assertThrows(IllegalArgumentException.class, () -> HashFunctions.copy(new HashFunction[]{ data -> 1, null }));
    }
}
