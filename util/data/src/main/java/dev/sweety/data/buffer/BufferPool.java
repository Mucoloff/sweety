package dev.sweety.data.buffer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Unified pooling façade over existing per-type allocators plus a scratch {@code byte[]} pool
 * for compression/decompression workloads.
 *
 * <p>Buffer acquisition delegates to the existing pooled allocators ({@link NioBufferAllocator#POOLED},
 * {@link SegmentBufferAllocator#POOLED}) which use thread-local deques — zero lock overhead,
 * same-thread release contract.
 *
 * <p>Scratch {@code byte[]} pool uses power-of-2 size buckets backed by a thread-local
 * {@code Deque[]}. Sizes outside the poolable range (< 64 bytes or > 1 MB) bypass the pool.
 */
public final class BufferPool {

    public static final BufferPool DEFAULT = new BufferPool();

    private final ThreadLocal<Deflater> deflaterLocal =
            ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION));
    private final ThreadLocal<Inflater> inflaterLocal =
            ThreadLocal.withInitial(Inflater::new);

    private static final int MIN_POOLED_BYTES  = 64;
    private static final int MAX_POOLED_BYTES  = 1 << 20; // 1 MB = MAX_PAYLOAD_SIZE
    private static final int MAX_BUCKET        = 20;       // log2(1MB)
    private static final int MAX_PER_BUCKET    = 8;

    @SuppressWarnings("unchecked")
    private final ThreadLocal<Deque<byte[]>[]> scratchPool = ThreadLocal.withInitial(() -> {
        Deque<byte[]>[] buckets = new Deque[MAX_BUCKET + 1];
        for (int i = 0; i <= MAX_BUCKET; i++) buckets[i] = new ArrayDeque<>();
        return buckets;
    });

    private BufferPool() {}

    // ===================== BUFFER ACQUISITION =====================

    public NioBuffer nio(int cap)     { return NioBufferAllocator.POOLED.buffer(cap); }
    public NioBuffer nio()            { return NioBufferAllocator.POOLED.buffer(); }
    public SegmentBuffer segment(int cap) { return SegmentBufferAllocator.POOLED.buffer(cap); }
    public SegmentBuffer segment()    { return SegmentBufferAllocator.POOLED.buffer(); }

    // ===================== DEFLATER / INFLATER =====================

    /** Returns the thread-local {@link Deflater}, reset and ready for new input. */
    public Deflater acquireDeflater() {
        Deflater d = deflaterLocal.get();
        d.reset();
        return d;
    }

    /** Returns the thread-local {@link Inflater}, reset and ready for new input. */
    public Inflater acquireInflater() {
        Inflater i = inflaterLocal.get();
        i.reset();
        return i;
    }

    // ===================== SCRATCH byte[] POOL =====================

    /**
     * Borrows a {@code byte[]} of at least {@code minLen} bytes from the thread-local pool.
     * The returned array may be larger (next power of 2). Caller must return it via
     * {@link #returnBytes(byte[])} after use.
     */
    public byte[] borrowBytes(int minLen) {
        if (minLen <= 0) return new byte[0];
        if (minLen > MAX_POOLED_BYTES) return new byte[minLen];

        int bucket = bucketFor(minLen);
        Deque<byte[]> deque = scratchPool.get()[bucket];
        byte[] arr = deque.poll();
        if (arr != null) return arr;
        return new byte[1 << bucket];
    }

    /**
     * Returns a previously borrowed array to the pool. Arrays outside the poolable size range
     * are silently dropped.
     */
    public void returnBytes(byte[] arr) {
        if (arr == null) return;
        int len = arr.length;
        if (len < MIN_POOLED_BYTES || len > MAX_POOLED_BYTES) return;
        if (!isPowerOf2(len)) return; // only pool power-of-2 arrays (borrowed from this pool)

        int bucket = Integer.numberOfTrailingZeros(len);
        Deque<byte[]> deque = scratchPool.get()[bucket];
        if (deque.size() < MAX_PER_BUCKET) deque.push(arr);
    }

    // ===================== HELPERS =====================

    private static int bucketFor(int minLen) {
        // smallest bucket b such that 2^b >= minLen
        if (minLen <= 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(minLen - 1);
    }

    private static boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
