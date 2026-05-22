package dev.sweety.data.buffer;

import dev.sweety.math.pool.ArrayPool;

import java.util.zip.CRC32C;
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
 * <p>Scratch {@code byte[]} pool uses power-of-2 size buckets, each backed by an
 * {@link ArrayPool#threadLocal} instance. Sizes outside the poolable range (&lt; 64 bytes or
 * &gt; 1 MB) bypass the pool.
 */
public final class BufferPool {

    public static final BufferPool DEFAULT = new BufferPool();

    private final ThreadLocal<Deflater> deflaterLocal =
            ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION));
    private final ThreadLocal<Inflater> inflaterLocal =
            ThreadLocal.withInitial(Inflater::new);
    private final ThreadLocal<CRC32C> crc32cLocal =
            ThreadLocal.withInitial(CRC32C::new);

    private static final int MIN_POOLED_BYTES = 64;
    private static final int MAX_POOLED_BYTES = 1 << 20; // 1 MB
    private static final int MAX_BUCKET = 20;       // log2(1 MB)
    private static final int MAX_PER_BUCKET = 8;

    private final ArrayPool<byte[]>[] scratchBuckets = createBuckets();


    private static ArrayPool<byte[]>[] createBuckets() {
        //noinspection unchecked
        ArrayPool<byte[]>[] buckets = new ArrayPool[MAX_BUCKET + 1];
        for (int i = 0; i <= MAX_BUCKET; i++)
            buckets[i] = ArrayPool.threadLocal(byte[]::new, a -> a.length, 1 << i, MAX_PER_BUCKET);
        return buckets;
    }

    private BufferPool() {
    }

    // ===================== BUFFER ACQUISITION =====================

    public NioBuffer nio(int cap) {
        return NioBufferAllocator.POOLED.buffer(cap);
    }

    public NioBuffer nio() {
        return NioBufferAllocator.POOLED.buffer();
    }

    public SegmentBuffer segment(int cap) {
        return SegmentBufferAllocator.POOLED.buffer(cap);
    }

    public SegmentBuffer segment() {
        return SegmentBufferAllocator.POOLED.buffer();
    }

    // ===================== DEFLATER / INFLATER =====================

    /**
     * Returns the thread-local {@link Deflater}, reset and ready for new input.
     */
    public Deflater acquireDeflater() {
        Deflater d = deflaterLocal.get();
        d.reset();
        return d;
    }

    /**
     * Returns the thread-local {@link Inflater}, reset and ready for new input.
     */
    public Inflater acquireInflater() {
        Inflater i = inflaterLocal.get();
        i.reset();
        return i;
    }

    /**
     * Returns the thread-local {@link CRC32C}, reset and ready for new input.
     */
    public CRC32C acquireCrc32c() {
        CRC32C c = crc32cLocal.get();
        c.reset();
        return c;
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
        return scratchBuckets[bucket].acquire(1 << bucket);
    }

    /**
     * Returns a previously borrowed array to the pool. Arrays outside the poolable size range
     * are silently dropped.
     */
    public void returnBytes(byte[] arr) {
        if (arr == null) return;
        int len = arr.length;
        if (len < MIN_POOLED_BYTES || len > MAX_POOLED_BYTES || !isPowerOf2(len)) return;
        scratchBuckets[Integer.numberOfTrailingZeros(len)].release(arr);
    }

    // ===================== HELPERS =====================

    private static int bucketFor(int minLen) {
        if (minLen <= 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(minLen - 1);
    }

    private static boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
