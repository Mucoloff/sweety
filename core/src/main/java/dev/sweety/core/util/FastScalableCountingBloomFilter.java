package dev.sweety.core.util;

import dev.sweety.core.crypt.ChecksumUtils;
import lombok.Getter;

public class FastScalableCountingBloomFilter {
    private byte[] filter;
    private final int hashFunctions;
    private final double growthFactor;

    @Getter
    private int elements;

    public FastScalableCountingBloomFilter(int initialSize, int hashFunctions, double growthFactor) {
        this.hashFunctions = hashFunctions;
        this.growthFactor = growthFactor;
        this.filter = new byte[initialSize];
        this.elements = 0;
    }

    private int[] getHashes(byte[] data) {
        int[] hashes = new int[hashFunctions];

        int h1 = ChecksumUtils.murmurHash3(data, 12345);
        int h2 = ChecksumUtils.crc32cInt(data, 54321);
        int h3 = ChecksumUtils.sha256Int(data, 12345);
        int h4 = ChecksumUtils.crc32Int(data, 54321);

        for (int i = 0; i < hashFunctions; i++) {
            // Combiniamo i tre hash diversi per generare k hash
            hashes[i] = Math.abs((h1 + i * h2 + i*i * h3 + i*i*i * h4) % filter.length);
        }
        return hashes;
    }

    public synchronized void add(byte[] data) {
        int[] hashes = getHashes(data);
        for (int h : hashes) {
            filter[h]++;
        }
        elements++;
        if (needsExpansion()) expand();
    }

    public synchronized boolean contains(byte[] data) {
        int[] hashes = getHashes(data);
        for (int h : hashes) {
            if (filter[h] == 0) return false;
        }
        return true;
    }

    public synchronized void remove(byte[] data) {
        int[] hashes = getHashes(data);
        for (int h : hashes) {
            if (filter[h] > 0) filter[h]--;
        }
        elements = Math.max(0, elements - 1);
    }

    private boolean needsExpansion() {
        double load = (double) elements / filter.length;
        return load > 0.7; // se saturazione >70%
    }

    private synchronized void expand() {
        int newSize = (int)(filter.length * growthFactor);
        byte[] newFilter = new byte[newSize];
        System.arraycopy(filter, 0, newFilter, 0, filter.length);
        filter = newFilter;
    }

    public double getEstimatedFPP() {
        // stima probabilità falsi positivi: (1 - e^(-k*n/m))^k
        double k = hashFunctions;
        double n = elements;
        double m = filter.length;
        return Math.pow(1 - Math.exp(-k * n / m), k);
    }

    public int getSize() {
        return filter.length;
    }

    // debug rapido
    @Override
    public String toString() {
        return "FastSCBF{size=" + filter.length + ", elements=" + elements + ", FPP=" + getEstimatedFPP() + "}";
    }

}
