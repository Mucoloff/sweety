
package dev.sweety.network.cloud.util;

import dev.sweety.network.cloud.packet.buffer.FileBuffer;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Utility standalone per misurare quando la compressione ZIP migliora o peggiora la dimensione.
 * Esegue test su diversi pattern e dimensioni e stampa CSV con i risultati.
 * <p>
 * Uso: esegui la main nel contesto del progetto (gradle run / java classpath ...)
 */
public final class CompressionProbe {
    private static final int[] SIZES;

    static {

        int iterations = 16;
        int counter = 8;
        SIZES = new int[iterations];
        for (int i = 0; i < iterations; i++, counter <<= 1) SIZES[i] = counter;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("pattern,size,rawBytes,compressedBytes,ratio,compressTimeMicros");



        // patterns: random, repeated single byte, repeated text, low-entropy (zeros)
        byte[] random = generateRandom(SIZES[SIZES.length - 1]);
        byte[] repeated = generateRepeated(SIZES[SIZES.length - 1], (byte) 'A', (byte) 0xFF, (byte) 0x7E);
        byte[] text = generateRepeatedText(SIZES[SIZES.length - 1]);
        byte[] zeros = generateRepeated(SIZES[SIZES.length - 1], (byte) 0);

        runPattern("random", random);
        runPattern("repeated", repeated);
        runPattern("text", text);
        runPattern("zeros", zeros);

        // suggerimento automatico: per ogni pattern trova la prima size dove compressed < raw
        System.out.println("\nSuggested thresholds (first size where compression makes packet smaller):");
        System.out.println(suggestThreshold("random", random));
        System.out.println(suggestThreshold("repeated", repeated));
        System.out.println(suggestThreshold("text", text));
        System.out.println(suggestThreshold("zeros", zeros));
    }

    private static void runPattern(String name, byte[] backing) throws Exception {
        for (int size : SIZES) {
            byte[] raw = new byte[size];
            System.arraycopy(backing, 0, raw, 0, size);

            long t0 = System.nanoTime();
            byte[] zipped = FileBuffer.zipByteArray(raw, "entry");
            long t1 = System.nanoTime();
            long micros = (t1 - t0) / 1_000;

            int rawLen = raw.length;
            int zippedLen = zipped.length;
            double ratio = (double) zippedLen / (double) rawLen;

            System.out.printf("%s,%d,%d,%d,%.4f,%d\n", name, size, rawLen, zippedLen, ratio, micros);
        }
    }

    private static String suggestThreshold(String name, byte[] backing) throws Exception {
        for (int size : SIZES) {
            byte[] raw = new byte[size];
            System.arraycopy(backing, 0, raw, 0, size);
            byte[] zipped = FileBuffer.zipByteArray(raw, "entry");
            if (zipped.length < raw.length) {
                return name + ": " + size + " bytes (compressed " + zipped.length + " < raw " + raw.length + ")";
            }
        }
        return name + ": no benefit up to " + SIZES[SIZES.length - 1] + " bytes";
    }

    private static byte[] generateRandom(int size) {
        byte[] b = new byte[size];
        new Random().nextBytes(b);
        return b;
    }

    private static byte[] generateRepeated(int size, byte... values) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) b[i] = values[i % values.length];
        return b;
    }

    private static byte[] generateRepeatedText(int size) {
        String base = "Questo è un messaggio di test numero ";
        byte[] baseBytes = base.getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[size];
        int off = 0;
        while (off < size) {
            int toCopy = Math.min(baseBytes.length, size - off);
            System.arraycopy(baseBytes, 0, b, off, toCopy);
            off += toCopy;
        }
        return b;
    }
}

