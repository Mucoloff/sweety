package dev.sweety.network.cloud.packet.buffer;

import dev.sweety.network.cloud.messaging.exception.PacketDecodeException;
import dev.sweety.network.cloud.packet.buffer.io.CallableDecoder;
import dev.sweety.network.cloud.packet.buffer.io.CallableEncoder;
import dev.sweety.network.cloud.packet.buffer.io.Decoder;
import dev.sweety.network.cloud.packet.buffer.io.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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

    public void writeFloat(float value) {
        this.nettyBuffer.writeFloat(value);
    }

    public float readFloat() {
        return this.nettyBuffer.readFloat();
    }

    public void writeLong(long value) {
        this.nettyBuffer.writeLong(value);
    }

    public long readLong() {
        return this.nettyBuffer.readLong();
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

    public void writeEnum(Enum<?> value) {
        this.nettyBuffer.writeShort(value.ordinal());
    }

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        short ordinal = this.readShort();
        if (ordinal >= 0 && ordinal < constants.length) return constants[ordinal];
        else throw new PacketDecodeException("Invalid enum ordinal: " + ordinal);
    }

    public void writeUuid(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        if (this.nettyBuffer.readableBytes() < 16)
            throw new PacketDecodeException("Not enough readableBytes to read UUID: " + readableBytes() + " / 16");
        return new UUID(readLong(), readLong());
    }

    public void writeBytesArray(byte[] bytes) {
        writeInt(bytes.length);
        writeBytes(bytes);
    }

    public byte[] readBytesArray() {
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

    public <T> void writeObject(T object, CallableEncoder<T> encoder) {
        if (object == null) {
            writeBoolean(false);
            return;
        }

        writeBoolean(true);
        encoder.write(object, this);
    }

    public <T> T readObject(CallableDecoder<T> decoder) {
        if (!readBoolean()) return null;
        return decoder.read(this);
    }


    public <T extends Decoder> T readObject(Supplier<T> factory) {
        if (!readBoolean()) return null;
        T object = factory.get();
        object.read(this);
        return object;
    }

    /**
     * Write a list of objects into the buffer, which can be encoded.
     *
     * @param collection The collection of data to store.
     * @param <T>        The type of the encoder object.
     */
    public <T extends Encoder> void writeCollection(Collection<T> collection) {
        writeCollection(collection, Encoder::write);
    }

    /**
     * Read a list of objects from the buffer, which can be encoded.
     *
     * @param factory The factory which creates an empty object based on {@code T}.
     * @param <T>     The typo of the decoder object.
     * @return The read list filled with optional data.
     */
    public <T extends Decoder> List<T> readCollection(Supplier<T> factory) {
        return readCollection(buffer -> {
            T instance = factory.get();
            instance.read(buffer);
            return instance;
        });
    }

    public <T> void writeCollection(Collection<T> collection, CallableEncoder<T> encoder) {
        writeInt(collection.size());
        for (T entry : collection) {
            encoder.write(entry, this);
        }
    }

    public <T> List<T> readCollection(CallableDecoder<T> decoder) {
        int size = readInt();
        List<T> data = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            data.add(decoder.read(this));
        }
        return data;
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
        byte[] bytes = new byte[readableBytes()];
        readBytes(bytes);
        return bytes;
    }

    public int readableBytes() {
        return this.nettyBuffer.readableBytes();
    }

    public void resetReaderIndex() {
        this.nettyBuffer.resetReaderIndex();
    }

    public void markReaderIndex() {
        this.nettyBuffer.markReaderIndex();
    }

    public void resetWriterIndex() {
        this.nettyBuffer.resetWriterIndex();
    }

    public void markWriterIndex() {
        this.nettyBuffer.markWriterIndex();
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
