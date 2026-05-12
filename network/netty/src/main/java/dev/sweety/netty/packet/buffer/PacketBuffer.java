package dev.sweety.netty.packet.buffer;

import dev.sweety.data.buffer.AbstractBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

public class PacketBuffer extends AbstractBuffer<PacketBuffer> {

    private final ByteBuf nettyBuffer;

    public PacketBuffer(ByteBuf nettyBuffer) {
        this.nettyBuffer = nettyBuffer;
    }

    public PacketBuffer() {
        this(PooledByteBufAllocator.DEFAULT.buffer(256));
    }

    public PacketBuffer(byte[] bytes) {
        this(Unpooled.wrappedBuffer(bytes));
    }

    public void clear() {
        this.nettyBuffer.clear();
    }

    //use writeVarInt
    @Deprecated
    public PacketBuffer writeInt(int value) {
        this.nettyBuffer.writeInt(value);
        return this;
    }

    //use readVarInt
    @Deprecated
    public int readInt() {
        return this.nettyBuffer.readInt();
    }

    public PacketBuffer writeDouble(double value) {
        this.nettyBuffer.writeDouble(value);
        return this;
    }

    public double readDouble() {
        return this.nettyBuffer.readDouble();
    }

    public PacketBuffer writeShort(short value) {
        this.nettyBuffer.writeShort(value);
        return this;
    }

    public short readShort() {
        return this.nettyBuffer.readShort();
    }

    public PacketBuffer writeByte(byte value) {
        this.nettyBuffer.writeByte(value);
        return this;
    }

    public byte readByte() {
        return this.nettyBuffer.readByte();
    }

    public PacketBuffer writeChar(char value) {
        this.nettyBuffer.writeChar(value);
        return this;
    }

    public char readChar() {
        return this.nettyBuffer.readChar();
    }

    public PacketBuffer writeFloat(float value) {
        this.nettyBuffer.writeFloat(value);
        return this;
    }

    public float readFloat() {
        return this.nettyBuffer.readFloat();
    }

    //use writeVarLong
    @Deprecated
    public PacketBuffer writeLong(long value) {
        this.nettyBuffer.writeLong(value);
        return this;
    }

    //use readVarLong
    @Deprecated
    public long readLong() {
        return this.nettyBuffer.readLong();
    }

    public short readUnsignedByte() {
        return this.nettyBuffer.readUnsignedByte();
    }

    public boolean release() {
        return this.nettyBuffer.release();
    }

    public PacketBuffer retain(int increment) {
        this.nettyBuffer.retain(increment);
        return this;
    }

    public PacketBuffer retain() {
        this.nettyBuffer.retain();
        return this;
    }

    public int refCnt() {
        return this.nettyBuffer.refCnt();
    }

    public int readableBytes() {
        return this.nettyBuffer.readableBytes();
    }

    public PacketBuffer resetReaderIndex() {
        this.nettyBuffer.resetReaderIndex();
        return this;
    }

    public PacketBuffer markReaderIndex() {
        this.nettyBuffer.markReaderIndex();
        return this;
    }

    public int readerIndex() {
        return this.nettyBuffer.readerIndex();
    }

    public PacketBuffer readerIndex(int readerIndex) {
        this.nettyBuffer.readerIndex(readerIndex);
        return this;
    }

    public PacketBuffer resetWriterIndex() {
        this.nettyBuffer.resetWriterIndex();
        return this;
    }

    public PacketBuffer markWriterIndex() {
        this.nettyBuffer.markWriterIndex();
        return this;
    }

    public int writerIndex() {
        return this.nettyBuffer.writerIndex();
    }

    public PacketBuffer writerIndex(int writerIndex) {
        this.nettyBuffer.writerIndex(writerIndex);
        return this;
    }

    public PacketBuffer readBytes(byte[] data) {
        this.nettyBuffer.readBytes(data);
        return this;
    }

    public PacketBuffer writeBytes(byte[] data) {
        this.nettyBuffer.writeBytes(data);
        return this;
    }

    public PacketBuffer writeBytes(byte[] data, int offset, int length) {
        this.nettyBuffer.writeBytes(data, offset, length);
        return this;
    }

    // Write bytes from another ByteBuf without converting to byte[] externally
    public PacketBuffer writeBytes(ByteBuf src) {
        this.nettyBuffer.writeBytes(src);
        return this;
    }

    public PacketBuffer wrapData(Encoder encoder) {
        byte[] bytes = readAllBytes();
        encoder.write(this);
        return writeBytes(bytes);
    }

    // Prefer zero-copy when possible
    public PacketBuffer writeBuffer(PacketBuffer other) {
        // Avoid other.getBytes(); write directly from underlying ByteBuf
        this.nettyBuffer.writeBytes(other.nettyBuffer);
        return this;
    }

    public PacketBuffer readSlice(int length) {
        return new PacketBuffer(nettyBuffer.readSlice(length));
    }

    // Return a retained slice to safely pass across components without immediate copy
    public PacketBuffer readRetainedSlice(int length) {
        ByteBuf slice = nettyBuffer.readSlice(length);
        slice.retain();
        return new PacketBuffer(slice);
    }

    public PacketBuffer slice() {
        return new PacketBuffer(this.nettyBuffer.slice());
    }

    // Create a slice view from current readerIndex with specified length
    public PacketBuffer slice(int index, int length) {
        return new PacketBuffer(this.nettyBuffer.slice(index, length));
    }

    // Retained slice for longer-lived sharing
    public PacketBuffer retainedSlice(int index, int length) {
        ByteBuf slice = this.nettyBuffer.slice(index, length);
        slice.retain();
        return new PacketBuffer(slice);
    }

    public ByteBuf nettyBuffer() {
        return this.nettyBuffer;
    }

    @Override
    public PacketBuffer setByte(int index, byte value) {
        this.nettyBuffer.setByte(index, value);
        return this;
    }

    @Override
    public byte getByte(int index) {
        return this.nettyBuffer.getByte(index);
    }

    @Override
    public PacketBuffer setShort(int index, short value) {
        this.nettyBuffer.setShort(index, value);
        return this;
    }

    @Override
    public short getShort(int index) {
        return this.nettyBuffer.getShort(index);
    }

    @Override
    public PacketBuffer setInt(int index, int value) {
        this.nettyBuffer.setInt(index, value);
        return this;
    }


    @Override
    public int getInt(int index) {
        return this.nettyBuffer.getInt(index);
    }


    @Override
    public PacketBuffer setLong(int index, long value) {
        this.nettyBuffer.setLong(index, value);
        return this;
    }


    @Override
    public long getLong(int index) {
        return this.nettyBuffer.getLong(index);
    }


    @Override
    public PacketBuffer setFloat(int index, float value) {
        this.nettyBuffer.setFloat(index, value);
        return this;
    }


    @Override
    public float getFloat(int index) {
        return this.nettyBuffer.getFloat(index);
    }

    public PacketBuffer setDouble(int index, double value) {
        this.nettyBuffer.setDouble(index, value);
        return this;
    }


    @Override
    public double getDouble(int index) {
        return this.nettyBuffer.getDouble(index);
    }


    @Override
    public PacketBuffer setChar(int index, char value) {
        this.nettyBuffer.setChar(index, value);
        return this;
    }


    @Override
    public char getChar(int index) {
        return this.nettyBuffer.getChar(index);
    }

    @Override
    public boolean isReadable() {
        return this.nettyBuffer.isReadable();
    }


    @Override
    protected void getBytes(int index, byte[] dst) {
        this.nettyBuffer.getBytes(index, dst);
    }
}