package dev.sweety.versioning.server.util.http;

import dev.sweety.data.buffer.BufferPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

public class BandwidthLimiterPoolingTest {

    @Test
    public void testBandwidthLimiterTransferWithPooledBuffers() throws IOException {
        BandwidthLimiter limiter = BandwidthLimiter.perSecond(10_000_000); // 10 MB/s
        byte[] payload = new byte[128 * 1024]; // 128 KB
        new Random(42).nextBytes(payload);

        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long transferred = limiter.transfer(in, out, null);
        Assertions.assertEquals(payload.length, transferred);
        Assertions.assertArrayEquals(payload, out.toByteArray());
    }

    @Test
    public void testBandwidthLimiterReadFullyWithPooledBuffers() throws IOException {
        BandwidthLimiter limiter = BandwidthLimiter.perSecond(10_000_000);
        byte[] payload = "Hello from Pooled Bandwidth Limiter!".getBytes();

        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        byte[] result = limiter.readFully(in, 1024);

        Assertions.assertArrayEquals(payload, result);
    }
}
