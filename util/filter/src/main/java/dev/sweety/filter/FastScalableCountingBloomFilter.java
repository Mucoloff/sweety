package dev.sweety.filter;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bloom filter counting scalabile (con ridimensionamento opzionale del vettore di bucket).
 * <p>Stessi punti API di {@link CountMinSketch} dove si applica:
 * {@link #add(byte[])}, {@link #elements()}, {@link #hashFunctionCount()}, {@link #bucketCount()},
 * {@link #indicesFor(byte[])}, oltre a {@link #contains(byte[])} e {@link #remove(byte[])}.</p>
 */
public class FastScalableCountingBloomFilter {

    private byte[] filter;
    private final HashFunction[] hashers;
    private final double growthFactor;
    private final LongAdder elements = new LongAdder();

    public int elements() {
        return (int) elements.sum();
    }

    @Deprecated
    public FastScalableCountingBloomFilter(int initialBucketCount, double growthFactor,
                                           Collection<? extends HashFunction> hashFunctions) {
        this.growthFactor = growthFactor;
        this.hashers = HashFunctions.copy(hashFunctions);
        this.filter = new byte[initialBucketCount];
    }

    /** @see #FastScalableCountingBloomFilter(int, double, Collection) */
    @Deprecated
    public FastScalableCountingBloomFilter(int initialBucketCount, double growthFactor, HashFunction... hashers) {
        if (Objects.requireNonNull(hashers, "hashers must not be null").length == 0) {
            throw new IllegalArgumentException("pass at least one HashFunction");
        }
        this.growthFactor = growthFactor;
        this.hashers = HashFunctions.copy(hashers);
        this.filter = new byte[initialBucketCount];
    }

    /** Hash Murmur predefinite; vedi {@link HashFunctions#murmur3Defaults(int)}. */
    @Deprecated
    public FastScalableCountingBloomFilter(int initialBucketCount, int hashFunctionCount, double growthFactor) {
        this(initialBucketCount, growthFactor, HashFunctions.murmur3Defaults(hashFunctionCount));
    }

    public static FastScalableCountingBloomFilter of(int initialBucketCount, double growthFactor,
                                                     Collection<? extends HashFunction> hashFunctions) {
        return new FastScalableCountingBloomFilter(initialBucketCount, growthFactor, hashFunctions);
    }

    public static FastScalableCountingBloomFilter of(int initialBucketCount, double growthFactor,
                                                     HashFunction... hashers) {
        return new FastScalableCountingBloomFilter(initialBucketCount, growthFactor, hashers);
    }

    public static FastScalableCountingBloomFilter of(int initialBucketCount, int hashFunctionCount,
                                                     double growthFactor) {
        return new FastScalableCountingBloomFilter(initialBucketCount, hashFunctionCount, growthFactor);
    }

    public synchronized void add(byte[] data) {
        int[] idx = indicesFor(data);
        for (int h : idx) {
            filter[h]++;
        }
        elements.increment();
        if (needsExpansion()) {
            expand();
        }
    }

    public synchronized boolean contains(byte[] data) {
        int[] idx = indicesFor(data);
        for (int h : idx) {
            if (filter[h] == 0) {
                return false;
            }
        }
        return true;
    }

    public synchronized void remove(byte[] data) {
        int[] idx = indicesFor(data);
        for (int h : idx) {
            if (filter[h] > 0) {
                filter[h]--;
            }
        }
        if (elements.sum() > 0) elements.add(-1L);
    }

    /** Stima probabilistica di falso positivo (model bloom classico). */
    public double estimatedFalsePositiveProbability() {
        int k = hashers.length;
        double n = elements.sum();
        double m = filter.length;
        return Math.pow(1 - Math.exp(-k * n / m), k);
    }

    /** Lunghezza attuale del vettore di bucket (post–eventuale {@link #add}). */
    public int bucketCount() {
        return filter.length;
    }

    /** Numero di funzioni hash. */
    public int hashFunctionCount() {
        return hashers.length;
    }

    /**
     * Come {@link CountMinSketch#indicesFor(byte[])}: indici modulo {@link #bucketCount()}.
     */
    public int[] indicesFor(byte[] data) {
        return HashFunctions.bucketIndices(data, hashers, filter.length);
    }

    private boolean needsExpansion() {
        double load = (double) elements.sum() / filter.length;
        return load > 0.7;
    }

    private synchronized void expand() {
        int newSize = (int) (filter.length * growthFactor);
        byte[] newFilter = new byte[newSize];
        System.arraycopy(filter, 0, newFilter, 0, filter.length);
        filter = newFilter;
    }

    @Override
    public String toString() {
        return "FastSCBF{bucketCount=%d, elements=%d, fpEst=%s}".formatted(filter.length, elements.sum(), estimatedFalsePositiveProbability());
    }
}
