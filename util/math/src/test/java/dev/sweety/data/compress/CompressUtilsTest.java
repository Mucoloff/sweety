package dev.sweety.data.compress;

import dev.sweety.data.buffer.BufferPool;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressUtilsTest {

    private static byte[] roundTrip(byte[] input) throws DataFormatException {
        Deflater deflater = BufferPool.DEFAULT.acquireDeflater();
        byte[] compressed = new byte[input.length + 64];
        int compLen = CompressUtils.deflate(input, input.length, compressed, deflater);
        BufferPool.DEFAULT.releaseDeflater(deflater);
        assertTrue(compLen > 0, "deflate returned " + compLen);

        Inflater inflater = BufferPool.DEFAULT.acquireInflater();
        byte[] output = new byte[input.length];
        int outLen = CompressUtils.inflate(compressed, compLen, output, input.length, inflater);
        BufferPool.DEFAULT.releaseInflater(inflater);
        assertEquals(input.length, outLen);
        return output;
    }

    @Test
    void roundTrip_singleByte() throws DataFormatException {
        byte[] data = {42};
        assertArrayEquals(data, roundTrip(data));
    }

    @Test
    void roundTrip_1KB() throws DataFormatException {
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte) 0xAB);
        assertArrayEquals(data, roundTrip(data));
    }

    @Test
    void roundTrip_100KB_random() throws DataFormatException {
        byte[] data = new byte[100 * 1024];
        new Random(0xDEAD).nextBytes(data);
        assertArrayEquals(data, roundTrip(data));
    }

    @Test
    void deflater_reset_between_calls() throws DataFormatException {
        byte[] a = "hello world".getBytes();
        byte[] b = "foo bar baz".getBytes();
        assertArrayEquals(a, roundTrip(a));
        assertArrayEquals(b, roundTrip(b));
    }

    @Test
    void dst_too_small_returns_negative() {
        byte[] data = new byte[1024];
        Arrays.fill(data, (byte) 0xAB);
        Deflater deflater = BufferPool.DEFAULT.acquireDeflater();
        byte[] tinyDst = new byte[4];
        int result = CompressUtils.deflate(data, data.length, tinyDst, deflater);
        BufferPool.DEFAULT.releaseDeflater(deflater);
        assertEquals(-1, result, "should return -1 when dst too small");
    }

    @Test
    void acquireDeflater_resets_state() {
        Deflater d = BufferPool.DEFAULT.acquireDeflater();
        d.setInput(new byte[]{1, 2, 3}, 0, 3);
        d.finish();
        BufferPool.DEFAULT.releaseDeflater(d);

        Deflater d2 = BufferPool.DEFAULT.acquireDeflater();
        assertFalse(d2.finished(), "Deflater must be reset on acquire");
        BufferPool.DEFAULT.releaseDeflater(d2);
    }

    @Test
    void acquireInflater_resets_state() throws DataFormatException {
        byte[] src = "test".getBytes();
        Deflater deflater = BufferPool.DEFAULT.acquireDeflater();
        byte[] comp = new byte[64];
        int compLen = CompressUtils.deflate(src, src.length, comp, deflater);
        BufferPool.DEFAULT.releaseDeflater(deflater);

        Inflater i = BufferPool.DEFAULT.acquireInflater();
        byte[] out = new byte[src.length];
        CompressUtils.inflate(comp, compLen, out, src.length, i);
        BufferPool.DEFAULT.releaseInflater(i);

        // re-acquire — must be reset and usable for a second inflate
        Inflater i2 = BufferPool.DEFAULT.acquireInflater();
        deflater = BufferPool.DEFAULT.acquireDeflater();
        compLen = CompressUtils.deflate(src, src.length, comp, deflater);
        BufferPool.DEFAULT.releaseDeflater(deflater);
        byte[] out2 = new byte[src.length];
        CompressUtils.inflate(comp, compLen, out2, src.length, i2);
        assertArrayEquals(src, out2);
        BufferPool.DEFAULT.releaseInflater(i2);
    }
}
