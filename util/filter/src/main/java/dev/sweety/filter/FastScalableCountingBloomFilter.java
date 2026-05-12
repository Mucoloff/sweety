package dev.sweety.filter;

import java.util.Collection;
import java.util.Objects;

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
    private int elements;

    public int elements() {
        return elements;
    }

    public FastScalableCountingBloomFilter(int initialBucketCount, double growthFactor,
                                           Collection<? extends HashFunction> hashFunctions) {
        this.growthFactor = growthFactor;
        this.hashers = HashFunctions.copy(hashFunctions);
        this.filter = new byte[initialBucketCount];
        this.elements = 0;
    }

    /** @see #FastScalableCountingBloomFilter(int, double, Collection) */
    public FastScalableCountingBloomFilter(int initialBucketCount, double growthFactor, HashFunction... hashers) {
        if (Objects.requireNonNull(hashers, "hashers must not be null").length == 0) {
            throw new IllegalArgumentException("pass at least one HashFunction");
        }
        this.growthFactor = growthFactor;
        this.hashers = HashFunctions.copy(hashers);
        this.filter = new byte[initialBucketCount];
        this.elements = 0;
    }

    /** Hash Murmur predefinite; vedi {@link HashFunctions#murmur3Defaults(int)}. */
    public FastScalableCountingBloomFilter(int initialBucketCount, int hashFunctionCount, double growthFactor) {
        this(initialBucketCount, growthFactor, HashFunctions.murmur3Defaults(hashFunctionCount));
    }

    public synchronized void add(byte[] data) {
        int[] idx = indicesFor(data);
        for (int h : idx) {
            filter[h]++;
        }
        elements++;
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
        elements = Math.max(0, elements - 1);
    }

    /** Stima probabilistica di falso positivo (model bloom classico). */
    public double estimatedFalsePositiveProbability() {
        int k = hashers.length;
        double n = elements;
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
        double load = (double) elements / filter.length;
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
        return "FastSCBF{bucketCount=" + filter.length + ", elements=" + elements
                + ", fpEst=" + estimatedFalsePositiveProbability() + "}";
    }
}
