package dev.sweety.core.crypt;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;

@UtilityClass
public class ChecksumUtils {

    private final CRC32C crc32c = new CRC32C();
    private final CRC32 crc32 = new CRC32();

    public CRC32C crc32c(boolean reset) {
        if (reset) crc32c.reset();
        return crc32c;
    }

    public CRC32 crc32(boolean reset) {
        if (reset) crc32.reset();
        return crc32;
    }

    public int crc32cInt(byte[] data, long seed) {
        crc32c.reset();
        crc32c.update(ByteBuffer.allocate(8).putLong(seed).array());
        crc32c.update(data);
        return (int) crc32c.getValue();
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

    public int murmurHash3(byte[] data, int seed) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int h1 = seed;
        while (buf.remaining() >= 4) {
            int k = buf.getInt();
            k *= 0xcc9e2d51;
            k = Integer.rotateLeft(k, 15);
            k *= 0x1b873593;
            h1 ^= k;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }
        int k1 = 0;
        int rem = buf.remaining();
        if (rem > 0) {
            for (int i = 0; i < rem; i++) {
                k1 ^= (buf.get() & 0xFF) << (i * 8);
            }
            k1 *= 0xcc9e2d51;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= 0x1b873593;
            h1 ^= k1;
        }
        h1 ^= data.length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);
        return h1;
    }
}
