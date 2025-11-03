package dev.sweety.network.cloud.packet.buffer;

import dev.sweety.network.cloud.packet.incoming.CallableDecoder;
import dev.sweety.network.cloud.packet.outgoing.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record PacketBuffer(ByteBuf nettyBuffer) {
    public PacketBuffer() {
        this(Unpooled.buffer());
    }

    public PacketBuffer(byte[] bytes) {
        this(Unpooled.wrappedBuffer(bytes));
    }

    public void clear() {
        this.nettyBuffer.clear();
    }

    public void writeInt(int value) {
        this.nettyBuffer.writeInt(value);
    }

    public int readInt() {
        return this.nettyBuffer.readInt();
    }

    public void writeDouble(double value) {
        this.nettyBuffer.writeDouble(value);
    }

    public double readDouble() {
        return this.nettyBuffer.readDouble();
    }

    public void writeShort(short value) {
        this.nettyBuffer.writeShort(value);
    }

    public short readShort() {
        return this.nettyBuffer.readShort();
    }

    public void writeByte(byte value) {
        this.nettyBuffer.writeByte(value);
    }

    public void writeBoolean(boolean value) {
        this.nettyBuffer.writeBoolean(value);
    }

    public float readFloat() {
        return this.nettyBuffer.readFloat();
    }

    public void replace(int offset, long newValue) {
        if (offset < 0 || offset + 8 > this.nettyBuffer.capacity())
            throw new IndexOutOfBoundsException("Invalid offset");

        this.nettyBuffer.setLong(offset, newValue);
    }

    public void replaceWithShift(int offset, long newValue) {
        if (offset < 0 || offset + 8 > this.nettyBuffer.capacity())
            throw new IndexOutOfBoundsException("Invalid offset");

        byte[] remaining = new byte[this.nettyBuffer.readableBytes() - (offset + 8)];
        this.nettyBuffer.getBytes(offset + 8, remaining);
        this.nettyBuffer.setLong(offset, newValue);
        this.nettyBuffer.setBytes(offset + 8, remaining);
    }

    public byte readByte() {
        return this.nettyBuffer.readByte();
    }

    public void writeLong(long lungo) {
        this.nettyBuffer.writeLong(lungo);
    }

    public void writeString(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        this.writeInt(bytes.length);
        this.nettyBuffer.writeBytes(bytes);
    }

    public String readString() {
        int length = this.readInt();
        byte[] bytes = new byte[length];
        this.nettyBuffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void writeFloat(float pitch) {
        this.nettyBuffer.writeFloat(pitch);
    }

    public void writeEnum(Enum<?> value) {
        this.nettyBuffer.writeByte(value.ordinal());
    }

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        byte ordinal = this.readByte();
        if (ordinal >= 0 && ordinal < constants.length) return constants[ordinal];
        else throw new IllegalArgumentException("Invalid enum ordinal: " + ordinal);
    }

    public void writeUuid(UUID uuid) {
        this.nettyBuffer.writeLong(uuid.getMostSignificantBits());
        this.nettyBuffer.writeLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        return new UUID(this.nettyBuffer.readLong(), this.nettyBuffer.readLong());
    }

    public void writeBytesArray(byte[] bytes) {
        writeInt(bytes.length);
        this.nettyBuffer.writeBytes(bytes);
    }

    public byte[] readByteArray() {
        int len = readInt();
        byte[] bytes = new byte[len];
        this.nettyBuffer.readBytes(bytes);
        return bytes;
    }

    public <T extends Encoder> void writeObject(T object) {
        if (object == null) {
            writeBoolean(false);
            return;
        }

        writeBoolean(true);
        object.write(this);
    }

    public <T> T readObject(CallableDecoder<T> decoder) {
        if (!readBoolean()) return null;
        return decoder.read(this);
    }

    public void release() {
        this.nettyBuffer.release();
    }

    public short readUnsignedByte() {
        return this.nettyBuffer.readUnsignedByte();
    }

    public boolean readBoolean() {
        return this.nettyBuffer.readBoolean();
    }

    public byte[] getBytes() {
        byte[] bytes = new byte[this.nettyBuffer.readableBytes()];
        this.nettyBuffer.readBytes(bytes);
        return bytes;
    }

    public long readLong() {
        return this.nettyBuffer.readLong();
    }

    public int readableBytes() {
        return this.nettyBuffer.readableBytes();
    }

    public void resetReaderIndex() {
        this.nettyBuffer.resetReaderIndex();
    }

    public void readBytes(byte[] data) {
        this.nettyBuffer.readBytes(data);
    }

    public void writeBytes(byte[] data) {
        this.nettyBuffer.writeBytes(data);
    }

    public void writeBytes(byte[] data, int offset, int length) {
        this.nettyBuffer.writeBytes(data, offset, length);
    }

    public void wrapData(Encoder encoder) {
        byte[] bytes = getBytes();
        encoder.write(this);
        writeBytes(bytes);
    }
}
