package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.serialization.format.StructuredSink;

import java.util.UUID;

/**
 * Bridges {@link BufferWriter} to the format-agnostic {@link StructuredSink} SPI.
 * {@link #enterField} and {@link #exitField} are no-ops — binary buffers are positional.
 */
public final class BufferSink implements StructuredSink {

    private final BufferWriter writer;

    public BufferSink(BufferWriter writer) {
        this.writer = writer;
    }

    public BufferWriter writer() {
        return writer;
    }

    @Override public void writeBool(boolean v)  { writer.writeBoolean(v); }
    @Override public void writeByte(byte v)     { writer.writeByte(v); }
    @Override public void writeShort(short v)   { writer.writeShort(v); }
    @Override public void writeChar(char v)     { writer.writeChar(v); }
    @Override public void writeInt(int v)       { writer.writeInt(v); }
    @Override public void writeLong(long v)     { writer.writeLong(v); }
    @Override public void writeFloat(float v)   { writer.writeFloat(v); }
    @Override public void writeDouble(double v) { writer.writeDouble(v); }
    @Override public void writeString(String v) { writer.writeString(v); }
    @Override public void writeUUID(UUID v)     { writer.writeUuid(v); }
    @Override public void writeBytes(byte[] v)  { writer.writeByteArray(v); }

    @Override public void enterField(String name) { /* no-op: binary format is positional */ }
    @Override public void exitField()             { /* no-op */ }
}
