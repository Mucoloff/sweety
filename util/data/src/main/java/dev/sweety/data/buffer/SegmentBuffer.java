package dev.sweety.data.buffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

public class SegmentBuffer extends AbstractBuffer<SegmentBuffer> {

    static final int DEFAULT_CAPACITY = 256;
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private static final ValueLayout.OfShort  S = ValueLayout.JAVA_SHORT_UNALIGNED .withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt    I = ValueLayout.JAVA_INT_UNALIGNED   .withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong   L = ValueLayout.JAVA_LONG_UNALIGNED  .withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat  F = ValueLayout.JAVA_FLOAT_UNALIGNED .withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble D = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfChar   C = ValueLayout.JAVA_CHAR_UNALIGNED  .withOrder(ByteOrder.BIG_ENDIAN);

    private Arena arena;
    private MemorySegment segment;
    private final boolean owner;
    private final boolean closeable;
    private final Consumer<SegmentBuffer> recycler;

    private int refCnt = 1;
    private int writerIndex, readerIndex, markReader, markWriter;

    // ===================== FACTORIES =====================

    /**
     * Confined arena — fastest, single-thread only.
     * Same contract as Netty's thread-local pooled buffer: release must happen on the allocating thread.
     */
    public static SegmentBuffer confined() { return confined(DEFAULT_CAPACITY); }
    public static SegmentBuffer confined(int capacity) {
        return new SegmentBuffer(Arena.ofConfined(), capacity, true, null);
    }

    /**
     * Shared arena — thread-safe, slight lock overhead on segment access.
     * Use when buffer ownership crosses thread boundaries.
     */
    public static SegmentBuffer shared() { return shared(DEFAULT_CAPACITY); }
    public static SegmentBuffer shared(int capacity) {
        return new SegmentBuffer(Arena.ofShared(), capacity, true, null);
    }

    /**
     * Auto arena — GC-managed, no explicit close required.
     * Useful for buffers with unclear ownership. Cannot be pooled.
     */
    public static SegmentBuffer automatic() { return automatic(DEFAULT_CAPACITY); }
    public static SegmentBuffer automatic(int capacity) {
        return new SegmentBuffer(Arena.ofAuto(), capacity, true, null);
    }

    /**
     * Global arena — never released, for static or process-lifetime data.
     * {@link #release()} is a no-op.
     */
    public static SegmentBuffer global(int capacity) {
        return new SegmentBuffer(Arena.global(), capacity, false, null);
    }

    // ===================== CONSTRUCTORS =====================

    /** @deprecated Use {@link #confined()}, {@link #shared()}, etc., or {@link SegmentBufferAllocator}. */
    @Deprecated
    public SegmentBuffer() { this(DEFAULT_CAPACITY); }

    /** @deprecated Use {@link #confined(int)}, {@link #shared(int)}, etc., or {@link SegmentBufferAllocator}. */
    @Deprecated
    public SegmentBuffer(int capacity) {
        this(Arena.ofConfined(), capacity, true, null);
    }

    /** Internal: slice view — non-owning, borrows arena from parent. */
    private SegmentBuffer(Arena arena, MemorySegment segment) {
        this.arena = arena;
        this.segment = segment;
        this.owner = false;
        this.closeable = false;
        this.recycler = null;
    }

    /** Internal: used by factory methods and {@link SegmentBufferAllocator}. */
    SegmentBuffer(Arena arena, int capacity, boolean closeable, Consumer<SegmentBuffer> recycler) {
        this.arena = arena;
        this.segment = arena.allocate(capacity);
        this.owner = true;
        this.closeable = closeable;
        this.recycler = recycler;
    }

    // ===================== MEMORY CONTROL =====================

    @Override
    public boolean release() {
        if (!owner) return false;
        if (--refCnt == 0) {
            if (recycler != null) {
                clear();
                recycler.accept(this);
            } else if (closeable) {
                arena.close();
            }
            return true;
        }
        return false;
    }

    @Override public SegmentBuffer retain()              { refCnt++; return this; }
    @Override public SegmentBuffer retain(int increment) { refCnt += increment; return this; }
    @Override public int refCnt()                        { return refCnt; }

    /** Reset state for pool reuse — called by {@link SegmentBufferAllocator} after reclaim. */
    @Override
    protected void poolReset() {
        refCnt = 1;
        clear();
    }

    /** Force-close arena — called by pool when buffer is discarded on overflow. */
    void closeArena() {
        arena.close();
    }

    // ===================== CLEAR =====================

    @Override
    public void clear() {
        writerIndex = 0;
        readerIndex = 0;
        markReader = 0;
        markWriter = 0;
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
    }

    // ===================== CAPACITY =====================

    @Override public int capacity()      { return (int) segment.byteSize(); }
    @Override public int writableBytes() { return capacity() - writerIndex; }

    @Override
    public SegmentBuffer ensureWritable(int minWritableBytes) {
        if (writableBytes() >= minWritableBytes) return this;
        if (!owner) throw new IllegalStateException("Cannot grow a non-owning SegmentBuffer");
        if (refCnt > 1) throw new IllegalStateException("Cannot grow a shared SegmentBuffer");

        int current = capacity();
        int minNew = writerIndex + minWritableBytes;
        int target = current;
        while (target < minNew) {
            target += (target >> 1);
            if (target < 0 || target > MAX_CAPACITY) { target = minNew; break; }
        }

        Arena newArena = Arena.ofConfined();
        MemorySegment newSegment = newArena.allocate(target);
        MemorySegment.copy(segment, 0, newSegment, 0, current);

        if (closeable) arena.close();
        this.arena = newArena;
        this.segment = newSegment;
        return this;
    }

    @Override
    public SegmentBuffer discardReadBytes() {
        if (readerIndex == 0) return this;
        int readable = readableBytes();
        if (readable > 0) {
            byte[] tmp = new byte[readable];
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, readerIndex, tmp, 0, readable);
            MemorySegment.copy(tmp, 0, segment, ValueLayout.JAVA_BYTE, 0, readable);
        }
        writerIndex = readable;
        readerIndex = 0;
        markReader = 0;
        markWriter = Math.min(markWriter, writerIndex);
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
        return this;
    }

    private long reserveAndAdvance(int bytes) {
        ensureWritable(bytes);
        long offset = writerIndex;
        writerIndex += bytes;
        return offset;
    }

    private long advanceRead(int bytes) {
        long offset = readerIndex;
        readerIndex += bytes;
        return offset;
    }

    // ===================== PRIMITIVES WRITE =====================

    // IMPORTANT: advance first (may swap this.segment via ensureWritable), then read this.segment
    @Override public SegmentBuffer writeByte(byte value)    { long off = reserveAndAdvance(Byte.BYTES);      segment.set(ValueLayout.JAVA_BYTE, off, value); return this; }
    @Override public SegmentBuffer writeShort(short value)  { long off = reserveAndAdvance(Short.BYTES);     segment.set(S, off, value); return this; }
    @Override public SegmentBuffer writeInt(int value)      { long off = reserveAndAdvance(Integer.BYTES);   segment.set(I, off, value); return this; }
    @Override public SegmentBuffer writeLong(long value)    { long off = reserveAndAdvance(Long.BYTES);      segment.set(L, off, value); return this; }
    @Override public SegmentBuffer writeFloat(float value)  { long off = reserveAndAdvance(Float.BYTES);     segment.set(F, off, value); return this; }
    @Override public SegmentBuffer writeDouble(double value){ long off = reserveAndAdvance(Double.BYTES);    segment.set(D, off, value); return this; }
    @Override public SegmentBuffer writeChar(char value)    { long off = reserveAndAdvance(Character.BYTES); segment.set(C, off, value); return this; }

    // ===================== PRIMITIVES READ =====================

    @Override public byte   readByte()         { return segment.get(ValueLayout.JAVA_BYTE, advanceRead(Byte.BYTES)); }
    @Override public short  readShort()        { return segment.get(S, advanceRead(Short.BYTES)); }
    @Override public int    readInt()          { return segment.get(I, advanceRead(Integer.BYTES)); }
    @Override public long   readLong()         { return segment.get(L, advanceRead(Long.BYTES)); }
    @Override public float  readFloat()        { return segment.get(F, advanceRead(Float.BYTES)); }
    @Override public double readDouble()       { return segment.get(D, advanceRead(Double.BYTES)); }
    @Override public char   readChar()         { return segment.get(C, advanceRead(Character.BYTES)); }
    @Override public short  readUnsignedByte() { return (short) (readByte() & 0xFF); }

    // ===================== RANDOM ACCESS =====================

    @Override public SegmentBuffer setByte(int idx, byte v)    { segment.set(ValueLayout.JAVA_BYTE, idx, v); return this; }
    @Override public byte           getByte(int idx)           { return segment.get(ValueLayout.JAVA_BYTE, idx); }
    @Override public SegmentBuffer setShort(int idx, short v)  { segment.set(S, idx, v); return this; }
    @Override public short          getShort(int idx)          { return segment.get(S, idx); }
    @Override public SegmentBuffer setInt(int idx, int v)      { segment.set(I, idx, v); return this; }
    @Override public int            getInt(int idx)            { return segment.get(I, idx); }
    @Override public SegmentBuffer setLong(int idx, long v)    { segment.set(L, idx, v); return this; }
    @Override public long           getLong(int idx)           { return segment.get(L, idx); }
    @Override public SegmentBuffer setFloat(int idx, float v)  { segment.set(F, idx, v); return this; }
    @Override public float          getFloat(int idx)          { return segment.get(F, idx); }
    @Override public SegmentBuffer setDouble(int idx, double v){ segment.set(D, idx, v); return this; }
    @Override public double         getDouble(int idx)         { return segment.get(D, idx); }
    @Override public SegmentBuffer setChar(int idx, char v)    { segment.set(C, idx, v); return this; }
    @Override public char           getChar(int idx)           { return segment.get(C, idx); }

    // ===================== STATE =====================

    @Override public boolean isReadable()  { return readerIndex < writerIndex; }
    @Override public int readableBytes()   { return writerIndex - readerIndex; }
    @Override public int readerIndex()     { return readerIndex; }
    @Override public int writerIndex()     { return writerIndex; }

    @Override
    public SegmentBuffer readerIndex(int readerIndex) {
        this.readerIndex = readerIndex;
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public SegmentBuffer writerIndex(int writerIndex) {
        this.writerIndex = writerIndex;
        resetPackedBooleanWriteState();
        return this;
    }

    @Override public SegmentBuffer markReaderIndex()  { markReader = readerIndex; return this; }
    @Override public SegmentBuffer markWriterIndex()  { markWriter = writerIndex; return this; }

    @Override
    public SegmentBuffer resetReaderIndex() {
        readerIndex = markReader;
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public SegmentBuffer resetWriterIndex() {
        writerIndex = markWriter;
        resetPackedBooleanWriteState();
        return this;
    }

    // ===================== BYTES =====================

    @Override
    protected void getBytes(int index, byte[] dst) {
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, index, dst, 0, dst.length);
    }

    @Override
    public SegmentBuffer readBytes(byte[] data) {
        getBytes(readerIndex, data);
        readerIndex += data.length;
        return this;
    }

    @Override
    public SegmentBuffer writeBytes(byte[] data) {
        ensureWritable(data.length);
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, writerIndex, data.length);
        writerIndex += data.length;
        return this;
    }

    @Override
    public SegmentBuffer writeBytes(byte[] data, int offset, int length) {
        ensureWritable(length);
        MemorySegment.copy(data, offset, segment, ValueLayout.JAVA_BYTE, writerIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public SegmentBuffer writeBuffer(SegmentBuffer other) {
        int len = other.readableBytes();
        ensureWritable(len);
        MemorySegment.copy(other.segment, ValueLayout.JAVA_BYTE, other.readerIndex,
                           this.segment, ValueLayout.JAVA_BYTE, this.writerIndex, len);
        other.readerIndex += len;
        other.resetPackedBooleanReadState();
        this.writerIndex += len;
        return this;
    }

    // ===================== BULK PRIMITIVE ARRAYS =====================
    // overrides: bulk native memcpy via MemorySegment.copy — strictly faster than per-element loop in base

    @Override
    public SegmentBuffer writeShortArray(short... array) {
        writeVarInt(array.length);
        int bytes = array.length * Short.BYTES;
        ensureWritable(bytes);
        MemorySegment.copy(array, 0, segment, S, writerIndex, array.length);
        writerIndex += bytes;
        return this;
    }

    @Override
    public short[] readShortArray() {
        int len = readBoundedLength("short[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Short.BYTES, "short[]");
        short[] arr = new short[len];
        MemorySegment.copy(segment, S, readerIndex, arr, 0, len);
        readerIndex += len * Short.BYTES;
        return arr;
    }

    @Override
    public SegmentBuffer writeIntArray(int... array) {
        writeVarInt(array.length);
        int bytes = array.length * Integer.BYTES;
        ensureWritable(bytes);
        MemorySegment.copy(array, 0, segment, I, writerIndex, array.length);
        writerIndex += bytes;
        return this;
    }

    @Override
    public int[] readIntArray() {
        int len = readBoundedLength("int[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Integer.BYTES, "int[]");
        int[] arr = new int[len];
        MemorySegment.copy(segment, I, readerIndex, arr, 0, len);
        readerIndex += len * Integer.BYTES;
        return arr;
    }

    @Override
    public SegmentBuffer writeFloatArray(float... array) {
        writeVarInt(array.length);
        int bytes = array.length * Float.BYTES;
        ensureWritable(bytes);
        MemorySegment.copy(array, 0, segment, F, writerIndex, array.length);
        writerIndex += bytes;
        return this;
    }

    @Override
    public float[] readFloatArray() {
        int len = readBoundedLength("float[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Float.BYTES, "float[]");
        float[] arr = new float[len];
        MemorySegment.copy(segment, F, readerIndex, arr, 0, len);
        readerIndex += len * Float.BYTES;
        return arr;
    }

    @Override
    public SegmentBuffer writeDoubleArray(double... array) {
        writeVarInt(array.length);
        int bytes = array.length * Double.BYTES;
        ensureWritable(bytes);
        MemorySegment.copy(array, 0, segment, D, writerIndex, array.length);
        writerIndex += bytes;
        return this;
    }

    @Override
    public double[] readDoubleArray() {
        int len = readBoundedLength("double[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Double.BYTES, "double[]");
        double[] arr = new double[len];
        MemorySegment.copy(segment, D, readerIndex, arr, 0, len);
        readerIndex += len * Double.BYTES;
        return arr;
    }

    @Override
    public SegmentBuffer writeCharArray(char... array) {
        writeVarInt(array.length);
        int bytes = array.length * Character.BYTES;
        ensureWritable(bytes);
        MemorySegment.copy(array, 0, segment, C, writerIndex, array.length);
        writerIndex += bytes;
        return this;
    }

    @Override
    public char[] readCharArray() {
        int len = readBoundedLength("char[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Character.BYTES, "char[]");
        char[] arr = new char[len];
        MemorySegment.copy(segment, C, readerIndex, arr, 0, len);
        readerIndex += len * Character.BYTES;
        return arr;
    }

    // ===================== INTEROP =====================

    /**
     * Returns a view of the readable region as a {@link ByteBuffer}.
     * Backed by the same off-heap memory — mutations are immediately visible in both directions.
     * The view is valid only while this buffer's arena is open.
     */
    public ByteBuffer asNioBuffer() {
        return segment.asSlice(readerIndex, readableBytes()).asByteBuffer().order(ByteOrder.BIG_ENDIAN);
    }

    /**
     * Wraps a {@link ByteBuffer} as a non-owning {@link SegmentBuffer}.
     * The returned buffer borrows memory from {@code bb} — valid while {@code bb} is reachable.
     */
    public static SegmentBuffer wrap(ByteBuffer bb) {
        ByteBuffer sliced = bb.slice();
        MemorySegment seg = MemorySegment.ofBuffer(sliced);
        SegmentBuffer buf = new SegmentBuffer(null, seg);
        buf.writerIndex = sliced.capacity();
        return buf;
    }

    // ===================== SLICES (ZERO COPY) =====================

    @Override public SegmentBuffer slice() { return slice(readerIndex, readableBytes()); }

    @Override
    public SegmentBuffer slice(int index, int length) {
        return new SegmentBuffer(arena, segment.asSlice(index, length));
    }

    @Override
    public SegmentBuffer readSlice(int length) {
        SegmentBuffer s = slice(readerIndex, length);
        readerIndex += length;
        return s;
    }

    @Override public SegmentBuffer readRetainedSlice(int length)         { return readSlice(length).retain(); }
    @Override public SegmentBuffer retainedSlice(int index, int length)  { return slice(index, length).retain(); }
}
