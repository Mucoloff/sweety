package dev.sweety.data.buffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class SegmentBuffer extends AbstractBuffer<SegmentBuffer> {

    private final Arena arena;
    private final MemorySegment segment;
    private final boolean owner;

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

    // ===================== PRIMITIVES WRITE =====================

    @Override
    public SegmentBuffer writeByte(byte value) {
        segment.set(ValueLayout.JAVA_BYTE, writerIndex, value);
        writerIndex += Byte.BYTES;
        return this;
    }

    @Override
    public SegmentBuffer writeShort(short value) {
        segment.set(ValueLayout.JAVA_SHORT, writerIndex, value);
        writerIndex += Short.BYTES;
        return this;
    }

    @Override
    public SegmentBuffer writeInt(int value) {
        segment.set(ValueLayout.JAVA_INT, writerIndex, value);
        writerIndex += Integer.BYTES;
        return this;
    }

    @Override
    public SegmentBuffer writeLong(long value) {
        segment.set(ValueLayout.JAVA_LONG, writerIndex, value);
        writerIndex += Long.BYTES;
        return this;
    }

    @Override
    public SegmentBuffer writeFloat(float value) {
        segment.set(ValueLayout.JAVA_FLOAT, writerIndex, value);
        writerIndex += Float.BYTES;
        return this;
    }

    @Override
    public SegmentBuffer writeDouble(double value) {
        segment.set(ValueLayout.JAVA_DOUBLE, writerIndex, value);
        writerIndex += Double.BYTES;
        return this;
    }

    @Override
    public SegmentBuffer writeChar(char value) {
        segment.set(ValueLayout.JAVA_CHAR, writerIndex, value);
        writerIndex += Character.BYTES;
        return this;
    }

    // ===================== PRIMITIVES READ =====================

    @Override
    public byte readByte() {
        byte v = segment.get(ValueLayout.JAVA_BYTE, readerIndex);
        readerIndex += Byte.BYTES;
        return v;
    }

    @Override
    public short readShort() {
        short v = segment.get(ValueLayout.JAVA_SHORT, readerIndex);
        readerIndex += Short.BYTES;
        return v;
    }

    @Override
    public int readInt() {
        int v = segment.get(ValueLayout.JAVA_INT, readerIndex);
        readerIndex += Integer.BYTES;
        return v;
    }

    @Override
    public long readLong() {
        long v = segment.get(ValueLayout.JAVA_LONG, readerIndex);
        readerIndex += Long.BYTES;
        return v;
    }

    @Override
    public float readFloat() {
        float v = segment.get(ValueLayout.JAVA_FLOAT, readerIndex);
        readerIndex += Float.BYTES;
        return v;
    }

    @Override
    public double readDouble() {
        double v = segment.get(ValueLayout.JAVA_DOUBLE, readerIndex);
        readerIndex += Double.BYTES;
        return v;
    }

    @Override
    public char readChar() {
        char v = segment.get(ValueLayout.JAVA_CHAR, readerIndex);
        readerIndex += Character.BYTES;
        return v;
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
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, writerIndex, data.length);
        writerIndex += data.length;
        return this;
    }

    @Override
    public SegmentBuffer writeBytes(byte[] data, int offset, int length) {
        MemorySegment.copy(data, offset, segment, ValueLayout.JAVA_BYTE, writerIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public SegmentBuffer writeBuffer(SegmentBuffer other) {
        byte[] tmp = new byte[other.readableBytes()];
        other.readBytes(tmp);
        writeBytes(tmp);
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
        return readSlice(length);
    }

    @Override
    public SegmentBuffer retainedSlice(int index, int length) {
        return slice(index, length);
    }
}