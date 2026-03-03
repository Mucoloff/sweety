package dev.sweety.core.filter;

import dev.sweety.core.crypt.ChecksumUtils;

public class CountMinSketch {

    private final int[][] table;
    private final int width;
    private final int depth;
    private int count;

    public CountMinSketch(int width, int depth) {
        this.width = width;
        this.depth = depth;
        this.table = new int[depth][width];
        this.count = 0;
    }

    public void add(byte[] data) {
        int[] hashes = this.getHashes(data);
        for (int i = 0; i < depth; i++) {

            if (this.table[i][hashes[i]] < Integer.MAX_VALUE) {
                this.table[i][hashes[i]]++;
            }
        }
        this.count++;
    }

    /**
     * Dimezza tutti i valori nella tabella.
     * Da chiamare periodicamente (es. ogni 60 secondi) per dare priorità
     * ai dati recenti e "dimenticare" quelli vecchi
     */
    public void age() {
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                // >> 1 == / 2
                this.table[i][j] >>= 1;
            }
        }
        this.count >>= 1;
    }

    public int estimate(byte[] data) {
        int[] hashes = this.getHashes(data);
        int res = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            res = Math.min(res, table[i][hashes[i]]);
        }
        return res;
    }

    private int[] getHashes(byte[] data) {
        final int[] hashes = new int[depth];

        // Murmur3
        final int h1 = ChecksumUtils.murmurHash3(data, 0x12345678);
        final int h2 = ChecksumUtils.murmurHash3(data, h1);

        for (int i = 0; i < depth; i++) {
            //  Kirsch-Mitzenmacher per generare k hash da soli 2
            hashes[i] = Math.abs((h1 + i * h2) % width);
        }
        return hashes;
    }
}