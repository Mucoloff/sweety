package dev.sweety.data.buffer.adapter;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.serialization.format.StructuredSink;
import dev.sweety.serialization.format.StructuredSource;

import java.util.Objects;
import java.util.UUID;

/**
 * Zero-allocation view bridging {@link BufferWriter} and {@link BufferReader} to
 * format-agnostic {@link StructuredSink} and {@link StructuredSource}.
 */
public final class BufferStructuredAdapter implements StructuredSink, StructuredSource {

    private final BufferWriter writer;
    private final BufferReader reader;

    public BufferStructuredAdapter(BufferWriter writer, BufferReader reader) {
        this.writer = writer;
        this.reader = reader;
    }

    public static BufferStructuredAdapter of(BufferWriter writer, BufferReader reader) {
        return new BufferStructuredAdapter(writer, reader);
    }

    public static StructuredSink ofSink(BufferWriter writer) {
        return new BufferStructuredAdapter(Objects.requireNonNull(writer, "writer"), null);
    }

    public static StructuredSource ofSource(BufferReader reader) {
        return new BufferStructuredAdapter(null, Objects.requireNonNull(reader, "reader"));
    }

    @Override
    public void writeBool(boolean v) {
        writer.writeBoolean(v);
    }

    @Override
    public void writeByte(byte v) {
        writer.writeByte(v);
    }

    @Override
    public void writeShort(short v) {
        writer.writeShort(v);
    }

    @Override
    public void writeChar(char v) {
        writer.writeChar(v);
    }

    @Override
    public void writeInt(int v) {
        writer.writeInt(v);
    }

    @Override
    public void writeLong(long v) {
        writer.writeLong(v);
    }

    @Override
    public void writeFloat(float v) {
        writer.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) {
        writer.writeDouble(v);
    }

    @Override
    public void writeString(String v) {
        writer.writeString(v);
    }

    @Override
    public void writeUUID(UUID v) {
        writer.writeUuid(v);
    }

    @Override
    public void writeBytes(byte[] v) {
        writer.writeByteArray(v);
    }

    @Override
    public boolean readBool() {
        return reader.readBoolean();
    }

    @Override
    public byte readByte() {
        return reader.readByte();
    }

    @Override
    public short readShort() {
        return reader.readShort();
    }

    @Override
    public char readChar() {
        return reader.readChar();
    }

    @Override
    public int readInt() {
        return reader.readInt();
    }

    @Override
    public long readLong() {
        return reader.readLong();
    }

    @Override
    public float readFloat() {
        return reader.readFloat();
    }

    @Override
    public double readDouble() {
        return reader.readDouble();
    }

    @Override
    public String readString() {
        return reader.readString();
    }

    @Override
    public UUID readUUID() {
        return reader.readUuid();
    }

    @Override
    public byte[] readBytes() {
        return reader.readByteArray();
    }

    @Override
    public void enterField(String name) {
        // Flat positional binary stream: field boundaries are implicit
    }

    @Override
    public void exitField() {
        // Flat positional binary stream: field boundaries are implicit
    }
}
