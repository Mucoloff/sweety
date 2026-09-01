package dev.sweety.data.buffer;

//import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import dev.sweety.math.pool.Pooled;
import dev.sweety.math.pool.Release;

@Pooled(pool = NioBufferAllocator.class)
public class NioBuffer extends AbstractBuffer<NioBuffer> {

    static final int DEFAULT_CAPACITY = 256;
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private ByteBuffer buffer;
    private final boolean owned;
    private final Consumer<NioBuffer> recycler;

    private int readerIndex, writerIndex;
    private int markedReader, markedWriter;
    private int refCnt = 1;

    // ===================== FACTORIES =====================

    public static NioBuffer heap()          { return heap(DEFAULT_CAPACITY); }
    public static NioBuffer heap(int cap)   { return new NioBuffer(ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN), true, null); }

    public static NioBuffer direct()        { return direct(DEFAULT_CAPACITY); }
    public static NioBuffer direct(int cap) { return new NioBuffer(ByteBuffer.allocateDirect(cap).order(ByteOrder.BIG_ENDIAN), true, null); }

    public static NioBuffer wrap(byte[] bytes) {
        NioBuffer buf = new NioBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN), false, null);
        buf.writerIndex = bytes.length;
        return buf;
    }

    public static NioBuffer wrap(ByteBuffer bb) {
        // slice() pins position→limit as the new capacity
        ByteBuffer sliced = bb.slice().order(ByteOrder.BIG_ENDIAN);
        NioBuffer buf = new NioBuffer(sliced, false, null);
        buf.writerIndex = sliced.capacity();
        return buf;
    }

    //public static NioBuffer fromSegment(MemorySegment seg) {return wrap(seg.asByteBuffer().order(ByteOrder.BIG_ENDIAN));}

    // ===================== CONSTRUCTORS =====================

    NioBuffer(ByteBuffer backing, boolean owned, Consumer<NioBuffer> recycler) {
        this.buffer  = backing;
        this.owned   = owned;
        this.recycler = recycler;
    }

    /** Slice constructor — non-owning, borrows backing store from parent. */
    private NioBuffer(ByteBuffer slice) {
        this.buffer   = slice;
        this.owned    = false;
        this.recycler = null;
        this.writerIndex = slice.capacity();
    }

    // ===================== LIFECYCLE =====================

    @Release
    @Override
    public boolean release() {
        if (--refCnt == 0) {
            if (recycler != null) {
                poolReset();
                recycler.accept(this);
            }
            // direct ByteBuffer: GC Cleaner handles deallocation
            return true;
        }
        return false;
    }

    @Override public NioBuffer retain()              { refCnt++; return this; }
    @Override public NioBuffer retain(int increment) { refCnt += increment; return this; }
    @Override public int       refCnt()              { return refCnt; }

    @Override
    protected void poolReset() {
        refCnt = 1;
        clear();
    }

    // ===================== CLEAR =====================

    @Override
    public void clear() {
        readerIndex  = 0;
        writerIndex  = 0;
        markedReader = 0;
        markedWriter = 0;
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
    }

    // ===================== CAPACITY =====================

    @Override public int capacity()      { return buffer.capacity(); }
    @Override public int writableBytes() { return capacity() - writerIndex; }

    @Override
    public NioBuffer ensureWritable(int min) {
        if (writableBytes() >= min) return this;
        if (refCnt > 1) throw new IllegalStateException("cannot grow shared buffer");

        int current = capacity();
        int minNew  = writerIndex + min;
        int target  = current;
        while (target < minNew) {
            target += (target >> 1);
            if (target < 0 || target > MAX_CAPACITY) { target = minNew; break; }
        }

        ByteBuffer newBuf = buffer.isDirect()
                ? ByteBuffer.allocateDirect(target)
                : ByteBuffer.allocate(target);
        newBuf.order(ByteOrder.BIG_ENDIAN);

        // copy [0, writerIndex) via duplicate with pinned position/limit
        ByteBuffer src = buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
        src.position(0).limit(writerIndex);
        newBuf.put(src);

        this.buffer = newBuf;
        return this;
    }

    @Override
    public NioBuffer discardReadBytes() {
        if (readerIndex == 0) return this;
        int readable = readableBytes();
        if (readable > 0) {
            // absolute bulk put (JDK 16+): dst=0, src=[readerIndex, readerIndex+readable)
            buffer.put(0, buffer, readerIndex, readable);
        }
        writerIndex  = readable;
        readerIndex  = 0;
        markedReader = 0;
        markedWriter = Math.min(markedWriter, writerIndex);
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
        return this;
    }

    // ===================== PRIMITIVES WRITE =====================

    @Override
    public NioBuffer writeByte(byte value) {
        ensureWritable(Byte.BYTES);
        buffer.put(writerIndex++, value);
        return this;
    }

    @Override
    public NioBuffer writeShort(short value) {
        ensureWritable(Short.BYTES);
        buffer.putShort(writerIndex, value);
        writerIndex += Short.BYTES;
        return this;
    }

    @Override
    @Deprecated
    public NioBuffer writeInt(int value) {
        ensureWritable(Integer.BYTES);
        buffer.putInt(writerIndex, value);
        writerIndex += Integer.BYTES;
        return this;
    }

    @Override
    @Deprecated
    public NioBuffer writeLong(long value) {
        ensureWritable(Long.BYTES);
        buffer.putLong(writerIndex, value);
        writerIndex += Long.BYTES;
        return this;
    }

    @Override
    public NioBuffer writeFloat(float value) {
        ensureWritable(Float.BYTES);
        buffer.putFloat(writerIndex, value);
        writerIndex += Float.BYTES;
        return this;
    }

    @Override
    public NioBuffer writeDouble(double value) {
        ensureWritable(Double.BYTES);
        buffer.putDouble(writerIndex, value);
        writerIndex += Double.BYTES;
        return this;
    }

    @Override
    public NioBuffer writeChar(char value) {
        ensureWritable(Character.BYTES);
        buffer.putChar(writerIndex, value);
        writerIndex += Character.BYTES;
        return this;
    }

    // ===================== PRIMITIVES READ =====================

    @Override public byte   readByte()         { return buffer.get(readerIndex++); }
    @Override public short  readUnsignedByte() { return (short) (buffer.get(readerIndex++) & 0xFF); }

    @Override public short  readShort()  { short  v = buffer.getShort(readerIndex);  readerIndex += Short.BYTES;     return v; }
    @Override @Deprecated
    public    int    readInt()    { int    v = buffer.getInt(readerIndex);    readerIndex += Integer.BYTES;   return v; }
    @Override @Deprecated
    public    long   readLong()   { long   v = buffer.getLong(readerIndex);   readerIndex += Long.BYTES;      return v; }
    @Override public float  readFloat()  { float  v = buffer.getFloat(readerIndex);  readerIndex += Float.BYTES;     return v; }
    @Override public double readDouble() { double v = buffer.getDouble(readerIndex); readerIndex += Double.BYTES;    return v; }
    @Override public char   readChar()   { char   v = buffer.getChar(readerIndex);   readerIndex += Character.BYTES; return v; }

    // ===================== RANDOM ACCESS =====================

    @Override public NioBuffer setByte(int idx, byte v)    { buffer.put(idx, v);      return this; }
    @Override public byte      getByte(int idx)            { return buffer.get(idx);              }
    @Override public NioBuffer setShort(int idx, short v)  { buffer.putShort(idx, v); return this; }
    @Override public short     getShort(int idx)           { return buffer.getShort(idx);         }
    @Override public NioBuffer setInt(int idx, int v)      { buffer.putInt(idx, v);   return this; }
    @Override public int       getInt(int idx)             { return buffer.getInt(idx);           }
    @Override public NioBuffer setLong(int idx, long v)    { buffer.putLong(idx, v);  return this; }
    @Override public long      getLong(int idx)            { return buffer.getLong(idx);          }
    @Override public NioBuffer setFloat(int idx, float v)  { buffer.putFloat(idx, v); return this; }
    @Override public float     getFloat(int idx)           { return buffer.getFloat(idx);         }
    @Override public NioBuffer setDouble(int idx, double v){ buffer.putDouble(idx, v);return this; }
    @Override public double    getDouble(int idx)          { return buffer.getDouble(idx);        }
    @Override public NioBuffer setChar(int idx, char v)    { buffer.putChar(idx, v);  return this; }
    @Override public char      getChar(int idx)            { return buffer.getChar(idx);          }

    // ===================== STATE =====================

    @Override public boolean isReadable()  { return readerIndex < writerIndex; }
    @Override public int readableBytes()   { return writerIndex - readerIndex; }
    @Override public int readerIndex()     { return readerIndex; }
    @Override public int writerIndex()     { return writerIndex; }

    @Override
    public NioBuffer readerIndex(int readerIndex) {
        this.readerIndex = readerIndex;
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public NioBuffer writerIndex(int writerIndex) {
        this.writerIndex = writerIndex;
        resetPackedBooleanWriteState();
        return this;
    }

    @Override public NioBuffer markReaderIndex() { markedReader = readerIndex; return this; }
    @Override public NioBuffer markWriterIndex() { markedWriter = writerIndex; return this; }

    @Override
    public NioBuffer resetReaderIndex() {
        readerIndex = markedReader;
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public NioBuffer resetWriterIndex() {
        writerIndex = markedWriter;
        resetPackedBooleanWriteState();
        return this;
    }

    // ===================== BYTES =====================

    @Override
    protected void getBytes(int index, byte[] dst) {
        buffer.get(index, dst);
    }

    @Override
    public NioBuffer readBytes(byte[] data) {
        buffer.get(readerIndex, data);
        readerIndex += data.length;
        return this;
    }

    @Override
    public NioBuffer readBytes(byte[] data, int offset, int length) {
        buffer.get(readerIndex, data, offset, length);
        readerIndex += length;
        return this;
    }

    @Override
    public NioBuffer writeBytes(byte[] data) {
        ensureWritable(data.length);
        buffer.put(writerIndex, data);
        writerIndex += data.length;
        return this;
    }

    @Override
    public NioBuffer writeBytes(byte[] data, int offset, int length) {
        ensureWritable(length);
        buffer.put(writerIndex, data, offset, length);
        writerIndex += length;
        return this;
    }

    @Override
    public NioBuffer writeBuffer(NioBuffer other) {
        int len = other.readableBytes();
        ensureWritable(len);
        buffer.put(writerIndex, other.buffer, other.readerIndex, len);
        other.readerIndex += len;
        other.resetPackedBooleanReadState();
        writerIndex += len;
        return this;
    }

    // ===================== SLICES =====================

    @Override public NioBuffer slice() { return slice(readerIndex, readableBytes()); }

    @Override
    public NioBuffer slice(int index, int length) {
        return new NioBuffer(buffer.slice(index, length).order(ByteOrder.BIG_ENDIAN));
    }

    @Override
    public NioBuffer readSlice(int length) {
        NioBuffer s = slice(readerIndex, length);
        readerIndex += length;
        return s;
    }

    @Override public NioBuffer readRetainedSlice(int length)        { return readSlice(length).retain(); }
    @Override public NioBuffer retainedSlice(int index, int length) { return slice(index, length).retain(); }

    // ===================== INTEROP =====================

    /**
     * View of the readable region as a {@link ByteBuffer}.
     * Backed by the same memory — mutations are immediately visible in both directions.
     */
    public ByteBuffer asNioBuffer() {
        return buffer.slice(readerIndex, readableBytes()).order(ByteOrder.BIG_ENDIAN);
    }

    /** Raw backing buffer — prefer {@link #asNioBuffer()} for read-only consumers. */
    public ByteBuffer backingBuffer() { return buffer; }
}
