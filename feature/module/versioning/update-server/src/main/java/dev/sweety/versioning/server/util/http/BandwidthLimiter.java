package dev.sweety.versioning.server.util.http;

import dev.sweety.data.buffer.BufferPool;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Blocking token-bucket bandwidth limiter. Replaces Guava {@code RateLimiter}.
 *
 * <p>Smooths throughput to {@code bytesPerSecond}: {@link #reserve(int)} schedules permits (bytes)
 * on a shared timeline and returns the nanos to wait. An idle limiter accumulates up to
 * {@code maxBurstSeconds} of permits, allowing a short burst before steady-state throttling
 * resumes — mirroring Guava's {@code SmoothBursty}.
 *
 * <p>Transfer helpers use <b>adaptive, irregular chunks</b>: the buffer is sized to roughly one
 * {@link #CADENCE_SECONDS} window of the configured rate, then grown toward {@link #MAX_CHUNK}
 * while the source keeps the buffer full and shrunk back toward the base when it can't — keeping
 * lock/sleep/syscall overhead low at high rates without bursting at low rates. Permits acquired
 * always equal the bytes actually written, so rate accounting stays exact.
 */
public final class BandwidthLimiter {

    /** Target time-slice per acquire; balances smoothness against lock/sleep overhead. */
    private static final double CADENCE_SECONDS = 0.05d; // 50 ms
    private static final int MIN_CHUNK = 4 * 1024;
    private static final int MAX_CHUNK = 256 * 1024;

    private final ReentrantLock lock = new ReentrantLock();
    private final double nanosPerPermit;
    private final long maxBurstNanos;
    private final int baseChunk;

    /** Earliest time (nanoTime) the next permit may be granted. */
    private long nextFreeNanos;

    private BandwidthLimiter(double bytesPerSecond, double maxBurstSeconds) {
        this.nanosPerPermit = 1_000_000_000.0d / bytesPerSecond;
        this.maxBurstNanos = (long) (maxBurstSeconds * 1_000_000_000.0d);
        this.baseChunk = clamp((int) (bytesPerSecond * CADENCE_SECONDS), MIN_CHUNK, MAX_CHUNK);
        this.nextFreeNanos = System.nanoTime();
    }

    public static BandwidthLimiter perSecond(double bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            throw new IllegalArgumentException("bytesPerSecond must be > 0, got " + bytesPerSecond);
        }
        return new BandwidthLimiter(bytesPerSecond, 1.0d);
    }

    /** Reserves {@code permits} bytes on the timeline; returns nanos to wait before using them. */
    public long reserve(int permits) {
        if (permits <= 0) return 0L;
        lock.lock();
        try {
            long now = System.nanoTime();
            long earliest = now - maxBurstNanos; // catch up after idle, capped at one burst window
            if (nextFreeNanos < earliest) {
                nextFreeNanos = earliest;
            }
            long wait = Math.max(0L, nextFreeNanos - now);
            nextFreeNanos += (long) (permits * nanosPerPermit);
            return wait;
        } finally {
            lock.unlock();
        }
    }

    /** Reserves and blocks for {@code permits} bytes. Restores interrupt flag and returns on interrupt. */
    public void acquire(int permits) {
        sleepNanos(reserve(permits));
    }

    /** Receives each chunk as it is written (e.g. {@code MessageDigest::update} or a live session sink). */
    @FunctionalInterface
    public interface ChunkSink {
        void accept(byte[] buf, int offset, int length);
    }

    /**
     * Streams {@code in} to {@code out} rate-limited with adaptive chunks, optionally feeding each
     * written chunk to {@code sink} (single pass — no extra read for hashing). Returns bytes transferred.
     *
     * @param sink nullable; when non-null, invoked with exactly the bytes written, in order
     */
    public long transfer(InputStream in, OutputStream out, ChunkSink sink) throws IOException {
        byte[] buf = BufferPool.DEFAULT.borrowBytes(MAX_CHUNK);
        try {
            int chunk = baseChunk;
            long total = 0L;
            int n;
            while ((n = in.read(buf, 0, Math.min(chunk, buf.length))) != -1) {
                if (n == 0) continue;
                long wait = reserve(n);
                sleepNanos(wait);
                out.write(buf, 0, n);
                if (sink != null) sink.accept(buf, 0, n);
                total += n;
                chunk = adapt(chunk, n, wait);
            }
            return total;
        } finally {
            BufferPool.DEFAULT.returnBytes(buf);
        }
    }

    /**
     * Writes an in-memory {@code data} buffer to {@code out} rate-limited with adaptive chunks,
     * optionally feeding {@code sink}. Returns bytes written.
     */
    public long transfer(byte[] data, OutputStream out, ChunkSink sink) throws IOException {
        int chunk = baseChunk, offset = 0;
        while (offset < data.length) {
            int n = Math.min(chunk, data.length - offset);
            long wait = reserve(n);
            sleepNanos(wait);
            out.write(data, offset, n);
            if (sink != null) sink.accept(data, offset, n);
            offset += n;
            chunk = adapt(chunk, n, wait);
        }
        return data.length;
    }

    /**
     * Reads {@code in} fully into a byte array, throttled, adaptive chunks.
     *
     * @param maxBytes hard cap; exceeding it throws {@link IOException} to bound memory/abuse.
     */
    public byte[] readFully(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buf = BufferPool.DEFAULT.borrowBytes(MAX_CHUNK);
        try {
            int chunk = baseChunk, total = 0, n;
            while ((n = in.read(buf, 0, Math.min(chunk, buf.length))) != -1) {
                if (n == 0) continue;
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Request body exceeds limit of " + maxBytes + " bytes");
                }
                acquire(n);
                out.write(buf, 0, n);
                chunk = adapt(chunk, n, 0L);
            }
            return out.toByteArray();
        } finally {
            BufferPool.DEFAULT.returnBytes(buf);
        }
    }

    /**
     * Adapts next chunk size: grow when the source filled the buffer (under-utilising the limiter →
     * bigger chunks cut per-chunk overhead), shrink when it under-fills (bursty/slow source → smaller
     * chunks keep latency low). Bounded to [baseChunk, MAX_CHUNK].
     */
    private int adapt(int chunk, int got, long waitNanos) {
        if (got >= chunk && waitNanos == 0L) {
            return Math.min(MAX_CHUNK, chunk + (chunk >> 1)); // ×1.5 up
        }
        if (got < (chunk >> 1)) {
            return Math.max(baseChunk, chunk >> 1); // ×0.5 down
        }
        return chunk;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static void sleepNanos(long nanos) {
        if (nanos <= 0) return;
        long millis = nanos / 1_000_000L;
        int rem = (int) (nanos % 1_000_000L);
        try {
            Thread.sleep(millis, rem);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
