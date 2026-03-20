package dev.sweety.core.file;

import java.io.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCompressor {

    private GzipCompressor() {
    }

    public static byte[] compress(byte[] data, byte[] SIGNATURE) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byteStream.write(SIGNATURE);

        try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
            zipStream.write(data);
            zipStream.flush();
        }

        return byteStream.toByteArray();
    }

    public static byte[] decompress(byte[] data, byte[] SIGNATURE) throws IOException {
        if (data.length < SIGNATURE.length) throw new IOException("Data too short for compressed format");

        byte[] signature = Arrays.copyOfRange(data, 0, SIGNATURE.length);
        if (!Arrays.equals(signature, SIGNATURE)) {
            return data;
        }

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data, SIGNATURE.length, data.length - SIGNATURE.length);
             GZIPInputStream zipStream = new GZIPInputStream(byteStream)) {
            return zipStream.readAllBytes();
        }
    }

    public static boolean compressed(byte[] data, byte[] SIGNATURE) {
        if (data.length < SIGNATURE.length) return false;
        byte[] sig = Arrays.copyOfRange(data, 0, SIGNATURE.length);
        return Arrays.equals(sig, SIGNATURE);
    }
}
