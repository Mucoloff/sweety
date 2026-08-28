package dev.sweety.serialization;

import dev.sweety.serialization.format.StructuredSink;
import dev.sweety.serialization.format.StructuredSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredDefaultsTest {

    // ── In-memory sink/source backed by an Object list ──────────────────────────

    static final class ListSink implements StructuredSink {
        final List<Object> buf = new ArrayList<>();

        @Override public void writeBool(boolean v)   { buf.add(v); }
        @Override public void writeByte(byte v)      { buf.add(v); }
        @Override public void writeShort(short v)    { buf.add(v); }
        @Override public void writeChar(char v)      { buf.add(v); }
        @Override public void writeInt(int v)        { buf.add(v); }
        @Override public void writeLong(long v)      { buf.add(v); }
        @Override public void writeFloat(float v)    { buf.add(v); }
        @Override public void writeDouble(double v)  { buf.add(v); }
        @Override public void writeString(String v)  { buf.add(v); }
        @Override public void writeUUID(UUID v)      { buf.add(v); }
        @Override public void writeBytes(byte[] v)   { buf.add(v); }
        @Override public void enterField(String name){ /* no-op */ }
        @Override public void exitField()            { /* no-op */ }
    }

    static final class ListSource implements StructuredSource {
        private final List<Object> buf;
        private int pos;

        ListSource(List<Object> buf) { this.buf = buf; }

        @SuppressWarnings("unchecked")
        private <T> T next() { return (T) buf.get(pos++); }

        @Override public boolean readBool()   { return next(); }
        @Override public byte readByte()      { return next(); }
        @Override public short readShort()    { return next(); }
        @Override public char readChar()      { return next(); }
        @Override public int readInt()        { return next(); }
        @Override public long readLong()      { return next(); }
        @Override public float readFloat()    { return next(); }
        @Override public double readDouble()  { return next(); }
        @Override public String readString()  { return next(); }
        @Override public UUID readUUID()      { return next(); }
        @Override public byte[] readBytes()   { return next(); }
        @Override public void enterField(String name) { /* no-op */ }
        @Override public void exitField()             { /* no-op */ }
    }

    static ListSink sink() { return new ListSink(); }
    static ListSource source(ListSink s) { return new ListSource(s.buf); }

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Test
    void primitiveRoundTrip() {
        ListSink s = sink();
        s.writeBool(true);
        s.writeInt(42);
        s.writeLong(Long.MAX_VALUE);
        s.writeDouble(3.14);
        s.writeString("hello");

        ListSource r = source(s);
        assertTrue(r.readBool());
        assertEquals(42, r.readInt());
        assertEquals(Long.MAX_VALUE, r.readLong());
        assertEquals(3.14, r.readDouble());
        assertEquals("hello", r.readString());
    }

    @Test
    void uuidRoundTrip() {
        UUID id = UUID.randomUUID();
        ListSink s = sink();
        s.writeUUID(id);
        assertEquals(id, source(s).readUUID());
    }

    @Test
    void bytesRoundTrip() {
        byte[] data = {1, 2, 3};
        ListSink s = sink();
        s.writeBytes(data);
        assertArrayEquals(data, source(s).readBytes());
    }

    @Test
    void collectionRoundTrip() {
        List<String> input = List.of("a", "b", "c");
        ListSink s = sink();
        s.writeCollection(input, (sink, v) -> sink.writeString(v));

        List<String> output = source(s).readList(src -> src.readString());
        assertEquals(input, output);
    }

    @Test
    void mapRoundTrip() {
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("x", 1);
        input.put("y", 2);

        ListSink s = sink();
        s.writeMap(input,
                (sink, k) -> sink.writeString(k),
                (sink, v) -> sink.writeInt(v));

        Map<String, Integer> output = source(s).readMap(
                src -> src.readString(),
                src -> src.readInt(),
                LinkedHashMap::new);
        assertEquals(input, output);
    }

    @Test
    void optionalPresentRoundTrip() {
        Optional<String> input = Optional.of("value");
        ListSink s = sink();
        s.writeOptional(input.orElse(null), (sink, v) -> sink.writeString(v));

        Optional<String> output = source(s).readOptional(src -> src.readString());
        assertEquals(input, output);
    }

    @Test
    void optionalEmptyRoundTrip() {
        ListSink s = sink();
        s.writeOptional((String) null, (sink, v) -> sink.writeString(v));

        Optional<String> output = source(s).readOptional(src -> src.readString());
        assertTrue(output.isEmpty());
    }

    @Test
    void enumRoundTrip() {
        ListSink s = sink();
        s.writeEnum(Thread.State.WAITING);

        Thread.State result = source(s).readEnum(Thread.State.class);
        assertEquals(Thread.State.WAITING, result);
    }

    @Test
    void writerAndReaderFunctionalInterfaces() {
        Writer<String, StructuredSink> w = (sink, v) -> sink.writeString(v);
        Reader<String, StructuredSource> r = src -> src.readString();

        ListSink s = sink();
        w.write(s, "test");
        assertEquals("test", r.read(source(s)));
    }

    @Test
    void serializerCombinesWriterAndReader() {
        Serializer<Integer, StructuredSink, StructuredSource> ser = new Serializer<>() {
            @Override public void write(StructuredSink sink, Integer v) { sink.writeInt(v); }
            @Override public Integer read(StructuredSource src) { return src.readInt(); }
        };

        ListSink s = sink();
        ser.write(s, 99);
        assertEquals(99, ser.read(source(s)));
    }
}
