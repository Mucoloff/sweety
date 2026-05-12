package dev.sweety.filter;

import dev.sweety.data.ChecksumUtils;

/**
 * Implementazione {@link HashFunction} basata su MurmurHash3 con seed fisso per istanza.
 */
public final class MurmurHasher implements HashFunction {

    private final int seed;

    public MurmurHasher(int seed) {
        this.seed = seed;
    }

    @Override
    public int hash(byte[] data) {
        return ChecksumUtils.murmurHash3(data, seed);
    }
}
