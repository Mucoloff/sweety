package dev.sweety.data.compress;

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

    private CompressUtils() {}
}
