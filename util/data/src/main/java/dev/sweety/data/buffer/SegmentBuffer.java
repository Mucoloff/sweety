package dev.sweety.data.buffer;

import java.lang.foreign.*;
import java.nio.ByteOrder;

public class TestBuffer extends AbstractBuffer<TestBuffer> {

    private final MemorySegment segment;

    private int writerIndex;

    private int readerIndex;

    @Override
    public void clear() {
    }

    @Override
    public TestBuffer writeInt(int value) {
        return this;
    }

    @Override
    public int readInt() {
        return 0;
    }

    @Override
    public TestBuffer writeDouble(double value) {
        return this;
    }

    @Override
    public double readDouble() {
        return 0;
    }

    @Override
    public TestBuffer writeShort(short value) {
        return this;
    }

    @Override
    public short readShort() {
        return 0;
    }

    @Override
    public TestBuffer writeByte(byte value) {
        return this;
    }

    @Override
    public byte readByte() {
        return 0;
    }

    @Override
    public TestBuffer setByte(int index, byte value) {
        return this;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public TestBuffer writeChar(char value) {
        return this;
    }

    @Override
    public char readChar() {
        return 0;
    }

    @Override
    public TestBuffer writeFloat(float value) {
        return this;
    }

    @Override
    public float readFloat() {
        return 0;
    }

    @Override
    public TestBuffer writeLong(long value) {
        return this;
    }

    @Override
    public long readLong() {
        return 0;
    }

    @Override
    public short readUnsignedByte() {
        return 0;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public TestBuffer retain(int increment) {
        return this;
    }

    @Override
    public TestBuffer retain() {
        return this;
    }

    @Override
    public int refCnt() {
        return 0;
    }

    @Override
    protected void getBytes(int index, byte[] dst) {

    }

    @Override
    public int readableBytes() {
        return 0;
    }

    @Override
    public TestBuffer resetReaderIndex() {
        return this;
    }

    @Override
    public TestBuffer markReaderIndex() {
        return this;
    }

    @Override
    public int readerIndex() {
        return 0;
    }

    @Override
    public TestBuffer readerIndex(int readerIndex) {
        return this;
    }

    @Override
    public TestBuffer resetWriterIndex() {
        return this;
    }

    @Override
    public TestBuffer markWriterIndex() {
        return this;
    }

    @Override
    public int writerIndex() {
        return 0;
    }

    @Override
    public TestBuffer writerIndex(int writerIndex) {
        return this;
    }

    @Override
    public TestBuffer readBytes(byte[] data) {
        return this;
    }

    @Override
    public TestBuffer writeBytes(byte[] data) {
        return this;
    }

    @Override
    public TestBuffer writeBytes(byte[] data, int offset, int length) {
        return this;
    }

    @Override
    public TestBuffer writeBuffer(TestBuffer other) {
        return this;
    }

    @Override
    public TestBuffer readSlice(int length) {
        return this;
    }

    @Override
    public TestBuffer readRetainedSlice(int length) {
        return this;
    }

    @Override
    public TestBuffer slice() {
        return this;
    }

    @Override
    public TestBuffer slice(int index, int length) {
        return this;
    }

    @Override
    public TestBuffer retainedSlice(int index, int length) {
        return this;
    }
}
