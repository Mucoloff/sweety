package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.serialization.format.StructuredSource;

import java.util.UUID;

/**
 * Bridges {@link BufferReader} to the format-agnostic {@link StructuredSource} SPI.
 * {@link #enterField} and {@link #exitField} are no-ops — binary buffers are positional.
 */
public final class BufferSource implements StructuredSource {

    private final BufferReader reader;

    public BufferSource(BufferReader reader) {
        this.reader = reader;
    }

    public BufferReader reader() {
        return reader;
    }

    @Override public boolean readBool()   { return reader.readBoolean(); }
    @Override public byte readByte()      { return reader.readByte(); }
    @Override public short readShort()    { return reader.readShort(); }
    @Override public char readChar()      { return reader.readChar(); }
    @Override public int readInt()        { return reader.readInt(); }
    @Override public long readLong()      { return reader.readLong(); }
    @Override public float readFloat()    { return reader.readFloat(); }
    @Override public double readDouble()  { return reader.readDouble(); }
    @Override public String readString()  { return reader.readString(); }
    @Override public UUID readUUID()      { return reader.readUuid(); }
    @Override public byte[] readBytes()   { return reader.readByteArray(); }

    @Override public void enterField(String name) { /* no-op: binary format is positional */ }
    @Override public void exitField()             { /* no-op */ }
}
