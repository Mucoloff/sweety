package dev.sweety.core.crypt;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.zip.CRC32C;

@UtilityClass
public class ChecksumUtils {

    private final CRC32C crc32 = new CRC32C();

    public CRC32C crc32(boolean reset) {
        if (reset) crc32.reset();
        return crc32;
    }

    public int crc32Int(byte[] data, long seed) {
        crc32.reset();
        crc32.update(ByteBuffer.allocate(8).putLong(seed).array());
        crc32.update(data);
        return (int) crc32.getValue();
    }

    public int sha256Int(byte[] data, int seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            byte[] hash = digest.digest(ByteBuffer.allocate(4).putInt(seed).array()); // mix seed
            // prendi i primi 4 byte come int
            return ByteBuffer.wrap(hash).getInt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public String getFileChecksum(File file) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return bytesToHex(digest.digest());
    }

    public String toHex(byte[] bytes) {
        long result = 0;
        for (byte aByte : bytes) result = (result << 8) | (aByte & 0xFF);
        return Long.toHexString(result);
    }

    public String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * MurmurHash3 a 32-bit. Ideale per Bloom Filters e Count-Min Sketch.
     * È un hash non crittografico ma con un'ottima distribuzione.
     */
    public static int murmurHash3(byte[] data, int seed) {
        int h1 = seed;
        int len = data.length;
        int nblocks = len / 4;

        // --- Body ---
        for (int i = 0; i < nblocks; i++) {
            int k1 = getBlock(data, i);

            k1 *= 0xcc9e2d51;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= 0x1b873593;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // --- Tail ---
        int k1 = 0;
        int tailIndex = nblocks * 4;
        switch (len & 3) {
            case 3:
                k1 ^= (data[tailIndex + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[tailIndex + 1] & 0xff) << 8;
            case 1:
                k1 ^= (data[tailIndex] & 0xff);
                k1 *= 0xcc9e2d51;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= 0x1b873593;
                h1 ^= k1;
        }

        // --- Finalization ---
        h1 ^= len;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

    private static int getBlock(byte[] data, int i) {
        int index = i * 4;
        return ((data[index] & 0xff)) |
                ((data[index + 1] & 0xff) << 8) |
                ((data[index + 2] & 0xff) << 16) |
                ((data[index + 3] & 0xff) << 24);
    }

    /**
     * Alternativa rapida se hai già un int come base.
     */
    public static int hashInt(int x) {
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }
}
