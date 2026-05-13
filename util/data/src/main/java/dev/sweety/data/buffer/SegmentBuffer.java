package dev.sweety.data.buffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Deque;

public class SegmentBuffer extends AbstractBuffer<SegmentBuffer> {

    private Arena arena;
    private MemorySegment segment;
    private final boolean owner;
    private Deque<Arena> oldArenas;

    private int refCnt = 1;

    private int writerIndex;
    private int readerIndex;

    private int markReader;
    private int markWriter;

    // ===================== CONSTRUCTORS =====================
    public SegmentBuffer() {
        this(256);
    }

    public SegmentBuffer(int capacity) {
        this.arena = Arena.ofConfined();
        this.segment = arena.allocate(capacity);
        this.owner = true;
    }

    private SegmentBuffer(Arena arena, MemorySegment segment, boolean owner) {
        this.arena = arena;
        this.segment = segment;
        this.owner = owner;
    }

    // ===================== MEMORY CONTROL =====================

    @Override
    public boolean release() {
        if (!owner) return false;

        if (--refCnt == 0) {
            arena.close();
            if (oldArenas != null) {
                while (!oldArenas.isEmpty()) {
                    try { oldArenas.pop().close(); } catch (Exception ignored) {}
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public SegmentBuffer retain() {
        refCnt++;
        return this;
    }

    @Override
    public SegmentBuffer retain(int increment) {
        refCnt += increment;
        return this;
    }

    @Override
    public int refCnt() {
        return refCnt;
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

    // ===================== MEMORY MANAGEMENT =====================

    @Override
    public int capacity() {
        return (int) segment.byteSize();
    }

    @Override
    public int writableBytes() {
        return capacity() - writerIndex;
    }

    @Override
    public SegmentBuffer ensureWritable(int minWritableBytes) {
        if (writableBytes() >= minWritableBytes) return this;
        if (!owner) throw new IllegalStateException("Cannot grow a non-owning SegmentBuffer");

        int targetCapacity = capacity();
        int minNewCapacity = writerIndex + minWritableBytes;
        while (targetCapacity < minNewCapacity) {
            targetCapacity += (targetCapacity >> 1); // grow by 1.5x
        }

        Arena newArena = Arena.ofConfined();
        MemorySegment newSegment = newArena.allocate(targetCapacity);
        MemorySegment.copy(segment, 0, newSegment, 0, capacity());

        if (oldArenas == null) oldArenas = new java.util.ArrayDeque<>();
        oldArenas.push(arena);

        this.arena = newArena;
        this.segment = newSegment;
        return this;
    }

    @Override
    public SegmentBuffer discardReadBytes() {
        if (readerIndex == 0) return this;
        int readable = readableBytes();
        if (readable > 0) {
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, readerIndex,
                               segment, ValueLayout.JAVA_BYTE, 0, readable);
        }
        writerIndex = readable;
        readerIndex = 0;
        markReader = 0;
        markWriter = Math.min(markWriter, writerIndex);
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
        return this;
    }

    private long reserveAndAdvance(ValueLayout layout) {
        int bytes = (int) layout.byteSize();
        ensureWritable(bytes);
        long offset = writerIndex;
        writerIndex += bytes;
        return offset;
    }

    // ===================== PRIMITIVES WRITE =====================

    @Override
    public SegmentBuffer writeByte(byte value) {
        segment.set(ValueLayout.JAVA_BYTE, reserveAndAdvance(ValueLayout.JAVA_BYTE), value);
        return this;
    }

    @Override
    public SegmentBuffer writeShort(short value) {
        segment.set(ValueLayout.JAVA_SHORT, reserveAndAdvance(ValueLayout.JAVA_SHORT), value);
        return this;
    }

    @Override
    public SegmentBuffer writeInt(int value) {
        segment.set(ValueLayout.JAVA_INT, reserveAndAdvance(ValueLayout.JAVA_INT), value);
        return this;
    }

    @Override
    public SegmentBuffer writeLong(long value) {
        segment.set(ValueLayout.JAVA_LONG, reserveAndAdvance(ValueLayout.JAVA_LONG), value);
        return this;
    }

    @Override
    public SegmentBuffer writeFloat(float value) {
        segment.set(ValueLayout.JAVA_FLOAT, reserveAndAdvance(ValueLayout.JAVA_FLOAT), value);
        return this;
    }

    @Override
    public SegmentBuffer writeDouble(double value) {
        segment.set(ValueLayout.JAVA_DOUBLE, reserveAndAdvance(ValueLayout.JAVA_DOUBLE), value);
        return this;
    }

    @Override
    public SegmentBuffer writeChar(char value) {
        segment.set(ValueLayout.JAVA_CHAR, reserveAndAdvance(ValueLayout.JAVA_CHAR), value);
        return this;
    }

    private long advanceRead(ValueLayout layout) {
        int bytes = (int) layout.byteSize();
        long offset = readerIndex;
        readerIndex += bytes;
        return offset;
    }

    // ===================== PRIMITIVES READ =====================

    @Override
    public byte readByte() {
        return segment.get(ValueLayout.JAVA_BYTE, advanceRead(ValueLayout.JAVA_BYTE));
    }

    @Override
    public short readShort() {
        return segment.get(ValueLayout.JAVA_SHORT, advanceRead(ValueLayout.JAVA_SHORT));
    }

    @Override
    public int readInt() {
        return segment.get(ValueLayout.JAVA_INT, advanceRead(ValueLayout.JAVA_INT));
    }

    @Override
    public long readLong() {
        return segment.get(ValueLayout.JAVA_LONG, advanceRead(ValueLayout.JAVA_LONG));
    }

    @Override
    public float readFloat() {
        return segment.get(ValueLayout.JAVA_FLOAT, advanceRead(ValueLayout.JAVA_FLOAT));
    }

    @Override
    public double readDouble() {
        return segment.get(ValueLayout.JAVA_DOUBLE, advanceRead(ValueLayout.JAVA_DOUBLE));
    }

    @Override
    public char readChar() {
        return segment.get(ValueLayout.JAVA_CHAR, advanceRead(ValueLayout.JAVA_CHAR));
    }

    @Override
    public short readUnsignedByte() {
        return (short) (readByte() & 0xFF);
    }

    // ===================== RANDOM ACCESS =====================

    @Override
    public SegmentBuffer setByte(int index, byte value) {
        segment.set(ValueLayout.JAVA_BYTE, index, value);
        return this;
    }

    @Override
    public byte getByte(int index) {
        return segment.get(ValueLayout.JAVA_BYTE, index);
    }

    @Override
    public SegmentBuffer setShort(int index, short value) {
        segment.set(ValueLayout.JAVA_SHORT, index, value);
        return this;
    }

    @Override
    public short getShort(int index) {
        return segment.get(ValueLayout.JAVA_SHORT, index);
    }

    @Override
    public SegmentBuffer setInt(int index, int value) {
        segment.set(ValueLayout.JAVA_INT, index, value);
        return this;
    }

    @Override
    public int getInt(int index) {
        return segment.get(ValueLayout.JAVA_INT, index);
    }

    @Override
    public SegmentBuffer setLong(int index, long value) {
        segment.set(ValueLayout.JAVA_LONG, index, value);
        return this;
    }

    @Override
    public long getLong(int index) {
        return segment.get(ValueLayout.JAVA_LONG, index);
    }

    @Override
    public SegmentBuffer setFloat(int index, float value) {
        segment.set(ValueLayout.JAVA_FLOAT, index, value);
        return this;
    }

    @Override
    public float getFloat(int index) {
        return segment.get(ValueLayout.JAVA_FLOAT, index);
    }

    @Override
    public SegmentBuffer setDouble(int index, double value) {
        segment.set(ValueLayout.JAVA_DOUBLE, index, value);
        return this;
    }

    @Override
    public double getDouble(int index) {
        return segment.get(ValueLayout.JAVA_DOUBLE, index);
    }

    @Override
    public SegmentBuffer setChar(int index, char value) {
        segment.set(ValueLayout.JAVA_CHAR, index, value);
        return this;
    }

    @Override
    public char getChar(int index) {
        return segment.get(ValueLayout.JAVA_CHAR, index);
    }

    // ===================== STATE =====================

    @Override
    public boolean isReadable() {
        return readerIndex < writerIndex;
    }

    @Override
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    @Override
    public int readerIndex() {
        return readerIndex;
    }

    @Override
    public SegmentBuffer readerIndex(int readerIndex) {
        this.readerIndex = readerIndex;
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public SegmentBuffer writerIndex(int writerIndex) {
        this.writerIndex = writerIndex;
        resetPackedBooleanWriteState();
        return this;
    }

    @Override
    public SegmentBuffer markReaderIndex() {
        markReader = readerIndex;
        return this;
    }

    @Override
    public SegmentBuffer resetReaderIndex() {
        readerIndex = markReader;
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public SegmentBuffer markWriterIndex() {
        markWriter = writerIndex;
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
        this.writerIndex += len;
        return this;
    }

    // ===================== SLICES (ZERO COPY) =====================

    @Override
    public SegmentBuffer slice() {
        return slice(readerIndex, readableBytes());
    }

    @Override
    public SegmentBuffer slice(int index, int length) {
        MemorySegment view = segment.asSlice(index, length);
        return new SegmentBuffer(arena, view, false);
    }

    @Override
    public SegmentBuffer readSlice(int length) {
        SegmentBuffer s = slice(readerIndex, length);
        readerIndex += length;
        return s;
    }

    @Override
    public SegmentBuffer readRetainedSlice(int length) {
        return readSlice(length).retain();
    }

    @Override
    public SegmentBuffer retainedSlice(int index, int length) {
        return slice(index, length).retain();
    }
}