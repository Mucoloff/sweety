package dev.sweety.data.buffer;

import dev.sweety.math.pool.Acquire;
import dev.sweety.math.pool.ArrayPool;
import dev.sweety.math.pool.Borrows;
import dev.sweety.math.pool.ObjectPool;

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

    // Shared pools (not ThreadLocal) so onDiscard=end() bounds native heap on thread death.
    // Size 16: up to 16 concurrent compression ops before new instances are created.
    private static final int CODEC_POOL_SIZE = 16;
    private final ObjectPool<Deflater> deflaterPool = ObjectPool.shared(() -> new Deflater(Deflater.DEFAULT_COMPRESSION))
            .reset(Deflater::reset)
            .onDiscard(Deflater::end)
            .maxSize(CODEC_POOL_SIZE)
            .build();
    private final ObjectPool<Inflater> inflaterPool = ObjectPool.shared(Inflater::new)
            .reset(Inflater::reset)
            .onDiscard(Inflater::end)
            .maxSize(CODEC_POOL_SIZE)
            .build();
    // CRC32C is pure Java — no native resources, ThreadLocal is fine.
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
            buckets[i] = new ArrayPool.Builder<>(byte[]::new, a -> a.length)
                    .defaultSize(1 << i)
                    .maxSize(MAX_PER_BUCKET)
                    .build();
        return buckets;
    }

    private BufferPool() {
    }

    // ===================== BUFFER ACQUISITION =====================

    @Acquire
    public NioBuffer nio(int cap) {
        return NioBufferAllocator.POOLED.buffer(cap);
    }

    @Acquire
    public NioBuffer nio() {
        return NioBufferAllocator.POOLED.buffer();
    }

    /*
    @Acquire
    public SegmentBuffer segment(int cap) {
        return SegmentBufferAllocator.POOLED.buffer(cap);
    }

    @Acquire
    public SegmentBuffer segment() {
        return SegmentBufferAllocator.POOLED.buffer();
    }
     */

    // ===================== DEFLATER / INFLATER =====================

    /**
     * Acquires a {@link Deflater} from the shared pool, already reset.
     * <strong>Must</strong> be returned via {@link #releaseDeflater(Deflater)} after use.
     */
    public Deflater acquireDeflater() {
        return deflaterPool.acquire();
    }

    /** Returns a previously acquired {@link Deflater} to the shared pool. */
    public void releaseDeflater(Deflater d) {
        deflaterPool.release(d);
    }

    /**
     * Acquires an {@link Inflater} from the shared pool, already reset.
     * <strong>Must</strong> be returned via {@link #releaseInflater(Inflater)} after use.
     */
    public Inflater acquireInflater() {
        return inflaterPool.acquire();
    }

    /** Returns a previously acquired {@link Inflater} to the shared pool. */
    public void releaseInflater(Inflater i) {
        inflaterPool.release(i);
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
    @Borrows
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
