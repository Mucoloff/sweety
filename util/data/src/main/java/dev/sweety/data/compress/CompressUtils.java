package dev.sweety.data.compress;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class CompressUtils {

    /**
     * Deflates {@code src[0..srcLen)} into {@code dst[0..)}.
     * Returns the number of bytes written, or -1 if {@code dst} is too small.
     */
    public static int deflate(byte[] src, int srcLen, byte[] dst, Deflater deflater) {
        deflater.setInput(src, 0, srcLen);
        deflater.finish();
        int written = 0;
        while (!deflater.finished() && written < dst.length) {
            written += deflater.deflate(dst, written, dst.length - written);
        }
        return deflater.finished() ? written : -1;
    }

    /**
     * Inflates {@code src[0..srcLen)} into {@code dst[0..dstLen)}.
     * {@code dstLen} must equal the original uncompressed length.
     * Returns the number of bytes written.
     */
    public static int inflate(byte[] src, int srcLen, byte[] dst, int dstLen, Inflater inflater)
            throws DataFormatException {
        inflater.setInput(src, 0, srcLen);
        return inflater.inflate(dst, 0, dstLen);
    }

    /**
     * Deflates {@code src} (position → limit) into {@code dst} (position → limit).
     * Advances both buffers' positions. Returns bytes written, or -1 if dst too small.
     * Uses {@link Deflater#setInput(ByteBuffer)} / {@link Deflater#deflate(ByteBuffer)} (JDK 11+),
     * avoiding a heap {@code byte[]} copy when src is a zero-copy NIO view.
     */
    public static int deflate(ByteBuffer src, ByteBuffer dst, Deflater deflater) {
        deflater.setInput(src);
        deflater.finish();
        int written = 0;
        while (!deflater.finished() && dst.hasRemaining()) {
            written += deflater.deflate(dst);
        }
        return deflater.finished() ? written : -1;
    }

    /**
     * Inflates {@code src} (position → limit) into {@code dst} (position → limit).
     * Returns bytes written.
     */
    public static int inflate(ByteBuffer src, ByteBuffer dst, Inflater inflater)
            throws DataFormatException {
        inflater.setInput(src);
        return inflater.inflate(dst);
    }

    private CompressUtils() {}
}
