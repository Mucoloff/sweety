package dev.sweety.persistence.config.compression;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCompressor {
    private static final byte[] SIGNATURE = {0x45, 0x43, 0x53, 0x54, 0x41, 0x43, 0x59};

    private GzipCompressor() {}

    public static byte[] compress(String data) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byteStream.write(SIGNATURE);

        try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
            zipStream.write(data.getBytes(StandardCharsets.UTF_8));
            zipStream.flush();
        }

        return byteStream.toByteArray();
    }

    public static String decompress(byte[] data) throws IOException {
        if (data.length < SIGNATURE.length) {
            throw new IOException("Data too short for compressed format");
        }

        byte[] signature = Arrays.copyOfRange(data, 0, SIGNATURE.length);
        if (!Arrays.equals(signature, SIGNATURE)) {
            return new String(data, StandardCharsets.UTF_8);
        }

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data, SIGNATURE.length, data.length - SIGNATURE.length);
             GZIPInputStream zipStream = new GZIPInputStream(byteStream)) {
            return new String(zipStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static boolean isCompressed(byte[] data) {
        if (data.length < SIGNATURE.length) return false;
        byte[] sig = Arrays.copyOfRange(data, 0, SIGNATURE.length);
        return Arrays.equals(sig, SIGNATURE);
    }
}
