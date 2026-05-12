package dev.sweety.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Fabbriche per array di hash da usare con {@link CountMinSketch} e {@link FastScalableCountingBloomFilter}.
 */
public final class HashFunctions {

    private static final int DEFAULT_BASE = 0x12345678;
    private static final int HASH_SPREAD = 0x9E3779B9;

    private HashFunctions() {
    }

    /**
     * {@code count} funzioni MurmurHash3 con seed distinti (adatto come default per sketch / bloom).
     */
    public static HashFunction[] murmur3Defaults(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        HashFunction[] h = new HashFunction[count];
        for (int i = 0; i < count; i++) {
            h[i] = new MurmurHasher(DEFAULT_BASE ^ (i * HASH_SPREAD));
        }
        return h;
    }

    /**
     * Copia difensiva; rifiuta elementi {@code null}.
     */
    public static HashFunction[] copy(HashFunction[] hashers) {
        if (hashers == null) {
            throw new IllegalArgumentException("hashFunctions array is null");
        }
        if (hashers.length == 0) {
            throw new IllegalArgumentException("at least one HashFunction is required");
        }
        HashFunction[] out = Arrays.copyOf(hashers, hashers.length);
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null) {
                throw new IllegalArgumentException("HashFunction at index " + i + " is null");
            }
        }
        return out;
    }

    /**
     * Come {@link #copy(HashFunction[])}, ma da {@link Collection} (lista, {@code Set}, ecc.).
     * <p>L'ordine delle funzioni è quello dell'iteratore della collezione ({@link java.util.HashSet}, ad es.,
     * non garantisce alcun ordine stabile).</p>
     */
    public static HashFunction[] copy(Collection<? extends HashFunction> hashFunctions) {
        if (hashFunctions == null) {
            throw new IllegalArgumentException("hashFunctions collection is null");
        }
        HashFunction[] out = hashFunctions.toArray(HashFunction[]::new);
        if (out.length == 0) {
            throw new IllegalArgumentException("at least one HashFunction is required");
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null) {
                throw new IllegalArgumentException("HashFunction at index " + i + " is null");
            }
        }
        return Arrays.copyOf(out, out.length);
    }

    /**
     * Un indice nel range {@code [0, bucketCount)} per ogni funzione ({@link Math#floorMod}).
     * Usato internamente da {@link CountMinSketch} e {@link FastScalableCountingBloomFilter}.
     */
    public static int[] bucketIndices(byte[] data, HashFunction[] hashers, int bucketCount) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(hashers, "hashers");
        if (hashers.length == 0) {
            throw new IllegalArgumentException("at least one HashFunction is required");
        }
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive: " + bucketCount);
        }
        int[] out = new int[hashers.length];
        for (int i = 0; i < hashers.length; i++) {
            HashFunction hf = hashers[i];
            if (hf == null) {
                throw new IllegalArgumentException("HashFunction at index " + i + " is null");
            }
            out[i] = Math.floorMod(hf.hash(data), bucketCount);
        }
        return out;
    }
}
