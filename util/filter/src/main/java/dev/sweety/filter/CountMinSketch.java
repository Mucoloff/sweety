package dev.sweety.filter;

import java.util.Collection;
import java.util.Objects;

/**
 * Count–Min Sketch: una riga per ogni hash ({@link #hashFunctionCount()}),
 * lunghezza fissa {@link #bucketCount()}.
 * <p>Allineamento con {@link FastScalableCountingBloomFilter}: {@link #add(byte[])},
 * {@link #elements()}, {@link #hashFunctionCount()}, {@link #bucketCount()},
 * {@link #indicesFor(byte[])}.</p>
 */
public class CountMinSketch {

    private final int[][] table;
    private final int bucketCount;
    private final HashFunction[] hashers;
    private int elements;

    /**
     * @param hashFunctions una funzione hash per riga; {@link #hashFunctionCount()}
     *                      coincide con il numero di elementi (ordine dall’iteratore)
     */
    public CountMinSketch(int bucketCount, Collection<? extends HashFunction> hashFunctions) {
        this.bucketCount = bucketCount;
        this.hashers = HashFunctions.copy(hashFunctions);
        this.table = new int[this.hashers.length][bucketCount];
        this.elements = 0;
    }

    /** @see #CountMinSketch(int, Collection) */
    public CountMinSketch(int bucketCount, HashFunction... hashers) {
        if (Objects.requireNonNull(hashers, "hashers must not be null").length == 0) {
            throw new IllegalArgumentException("pass at least one HashFunction");
        }
        this.bucketCount = bucketCount;
        this.hashers = HashFunctions.copy(hashers);
        this.table = new int[this.hashers.length][bucketCount];
        this.elements = 0;
    }

    /** Stesse hash di default Murmur degli altri costruttori con collection / varargs. */
    public CountMinSketch(int bucketCount, int hashFunctionCount) {
        this(bucketCount, HashFunctions.murmur3Defaults(hashFunctionCount));
    }

    public void add(byte[] data) {
        int[] idx = indicesFor(data);
        for (int i = 0; i < hashers.length; i++) {
            if (this.table[i][idx[i]] < Integer.MAX_VALUE) {
                this.table[i][idx[i]]++;
            }
        }
        this.elements++;
    }

    /**
     * Dimezza tutti i contatori nella tabella (es. per privilegiare traffico recente).
     */
    public void age() {
        for (int i = 0; i < hashers.length; i++) {
            for (int j = 0; j < bucketCount; j++) {
                this.table[i][j] >>= 1;
            }
        }
        this.elements >>= 1;
    }

    /** Stima inferiore della frequenza. */
    public int estimate(byte[] data) {
        int[] idx = indicesFor(data);
        int res = Integer.MAX_VALUE;
        for (int i = 0; i < hashers.length; i++) {
            res = Math.min(res, table[i][idx[i]]);
        }
        return res;
    }

    /** Aggiunte registrate dall’instanziazione. */
    public int elements() {
        return elements;
    }

    /** Numero di funzioni hash (righe nel sketch). */
    public int hashFunctionCount() {
        return hashers.length;
    }

    /** Larghezza di una riga (numero di contatori/bucket per riga). */
    public int bucketCount() {
        return bucketCount;
    }

    /**
     * Indici di bucket per ogni funzione hash ({@link Math#floorMod}); stesso contratto di
     * {@link FastScalableCountingBloomFilter#indicesFor(byte[])}.
     */
    public int[] indicesFor(byte[] data) {
        return HashFunctions.bucketIndices(data, hashers, bucketCount);
    }
}
