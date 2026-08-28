package dev.sweety.data.buffer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.sweety.data.buffer.BufferFactories.BOOL_ARRAY;
import static dev.sweety.data.buffer.BufferFactories.BYTE_VAL;
import static dev.sweety.data.buffer.BufferFactories.CHAR_ARRAY;
import static dev.sweety.data.buffer.BufferFactories.CHAR_VAL;
import static dev.sweety.data.buffer.BufferFactories.DOUBLE_ARRAY;
import static dev.sweety.data.buffer.BufferFactories.DOUBLE_VAL;
import static dev.sweety.data.buffer.BufferFactories.Factory;
import static dev.sweety.data.buffer.BufferFactories.FLOAT_ARRAY;
import static dev.sweety.data.buffer.BufferFactories.FLOAT_VAL;
import static dev.sweety.data.buffer.BufferFactories.INT_ARRAY;
import static dev.sweety.data.buffer.BufferFactories.LONG_VAL;
import static dev.sweety.data.buffer.BufferFactories.SHORT_ARRAY;
import static dev.sweety.data.buffer.BufferFactories.SHORT_VAL;
import static dev.sweety.data.buffer.BufferFactories.STR_VAL;
import static dev.sweety.data.buffer.BufferFactories.UUID_VAL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferRoundTripTest {

    enum Color { RED, GREEN, BLUE }
    enum Flag  { A, B, C, D }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void primitives(String name, Factory f) {
        var buf = f.create();

        buf.writeByte(BYTE_VAL);
        buf.writeShort(SHORT_VAL);
        buf.writeFloat(FLOAT_VAL);
        buf.writeDouble(DOUBLE_VAL);
        buf.writeChar(CHAR_VAL);

        assertEquals(BYTE_VAL,   buf.readByte());
        assertEquals(SHORT_VAL,  buf.readShort());
        assertEquals(FLOAT_VAL,  buf.readFloat(),  1e-6f);
        assertEquals(DOUBLE_VAL, buf.readDouble(), 1e-10);
        assertEquals(CHAR_VAL,   buf.readChar());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void varInt(String name, Factory f) {
        var buf = f.create();
        int[] values = {0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE, -1};
        for (int v : values) buf.writeVarInt(v);
        for (int v : values) assertEquals(v, buf.readVarInt(), "varInt " + v);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void varLong(String name, Factory f) {
        var buf = f.create();
        long[] values = {0L, 1L, Long.MAX_VALUE, -1L, LONG_VAL};
        for (long v : values) buf.writeVarLong(v);
        for (long v : values) assertEquals(v, buf.readVarLong(), "varLong " + v);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void string(String name, Factory f) {
        var buf = f.create();
        buf.writeString(STR_VAL);
        buf.writeString("");
        // writeString(null) encodes as VarInt(0) == empty string; reads back as ""
        buf.writeString(null);

        assertEquals(STR_VAL, buf.readString());
        assertEquals("",      buf.readString());
        assertEquals("",      buf.readString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void uuid(String name, Factory f) {
        var buf = f.create();
        buf.writeUuid(UUID_VAL);
        assertEquals(UUID_VAL, buf.readUuid());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void packedBooleans(String name, Factory f) {
        var buf = f.create();
        for (boolean b : BOOL_ARRAY) buf.writeBoolean(b);
        for (int i = 0; i < BOOL_ARRAY.length; i++) {
            assertEquals(BOOL_ARRAY[i], buf.readBoolean(), "bool[" + i + "]");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void intArray(String name, Factory f) {
        var buf = f.create();
        buf.writeIntArray(INT_ARRAY);
        assertArrayEquals(INT_ARRAY, buf.readIntArray());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void shortArray(String name, Factory f) {
        var buf = f.create();
        buf.writeShortArray(SHORT_ARRAY);
        assertArrayEquals(SHORT_ARRAY, buf.readShortArray());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void floatArray(String name, Factory f) {
        var buf = f.create();
        buf.writeFloatArray(FLOAT_ARRAY);
        float[] got = buf.readFloatArray();
        assertEquals(FLOAT_ARRAY.length, got.length);
        for (int i = 0; i < FLOAT_ARRAY.length; i++) assertEquals(FLOAT_ARRAY[i], got[i], 1e-5f);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void doubleArray(String name, Factory f) {
        var buf = f.create();
        buf.writeDoubleArray(DOUBLE_ARRAY);
        double[] got = buf.readDoubleArray();
        assertEquals(DOUBLE_ARRAY.length, got.length);
        for (int i = 0; i < DOUBLE_ARRAY.length; i++) assertEquals(DOUBLE_ARRAY[i], got[i], 1e-10);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void charArray(String name, Factory f) {
        var buf = f.create();
        buf.writeCharArray(CHAR_ARRAY);
        assertArrayEquals(CHAR_ARRAY, buf.readCharArray());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void list(String name, Factory f) {
        var buf = f.create();
        List<String> list = List.of("alpha", "beta", "γάμμα");
        buf.writeCollection(list, (b, s) -> b.writeString(s));
        List<String> got = buf.readList(b -> b.readString());
        assertEquals(list, got);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void map(String name, Factory f) {
        var buf = f.create();
        Map<Integer, String> map = Map.of(1, "one", 2, "two", 3, "three");
        buf.writeMap(map, (b, k) -> b.writeVarInt(k), (b, v) -> b.writeString(v));
        Map<Integer, String> got = buf.readMap(b -> b.readVarInt(), b -> b.readString(), java.util.HashMap::new);
        assertEquals(map, got);
    }

    // --- EnumMap ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumMap(String name, Factory f) {
        var buf = f.create();
        EnumMap<Color, String> src = new EnumMap<>(Color.class);
        src.put(Color.RED,  "red");
        src.put(Color.BLUE, "blue");
        // via generic writeMap — auto-detects EnumMap
        buf.writeMap(src, (b, k) -> b.writeVarInt(k.ordinal()), (b, v) -> b.writeString(v));
        EnumMap<Color, String> got = buf.readMap(Color.class, b -> b.readString());
        assertInstanceOf(EnumMap.class, got);
        assertEquals(src, got);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumMapEmpty(String name, Factory f) {
        var buf = f.create();
        EnumMap<Color, String> src = new EnumMap<>(Color.class);
        buf.writeMap(src, (b, k) -> b.writeVarInt(k.ordinal()), (b, v) -> b.writeString(v));
        EnumMap<Color, String> got = buf.readMap(Color.class, b -> b.readString());
        assertInstanceOf(EnumMap.class, got);
        assertEquals(src, got);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumMapNull(String name, Factory f) {
        var buf = f.create();
        buf.writeMap((EnumMap<Color, String>) null, (b, k) -> {}, (b, v) -> b.writeString(v));
        assertNull(buf.readMap(Color.class, b -> b.readString()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumMapGenericReadReturnsEmpty(String name, Factory f) {
        var buf = f.create();
        EnumMap<Color, String> src = new EnumMap<>(Color.class);
        src.put(Color.GREEN, "green");
        buf.writeMap(src, (b, k) -> b.writeVarInt(k.ordinal()), (b, v) -> b.writeString(v));
        // generic read cannot reconstruct enum keys — returns empty but keeps stream valid
        Map<Integer, String> got = buf.readMap(b -> b.readVarInt(), b -> b.readString(), java.util.HashMap::new);
        assertTrue(got.isEmpty());
        assertFalse(buf.isReadable());
    }

    // --- EnumSet ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumSet(String name, Factory f) {
        var buf = f.create();
        EnumSet<Flag> src = EnumSet.of(Flag.A, Flag.C);
        // via generic writeCollection — auto-detects EnumSet
        buf.writeCollection(src, (b, e) -> b.writeVarInt(e.ordinal()));
        EnumSet<Flag> got = buf.readCollection(Flag.class);
        assertEquals(src, got);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumSetFull(String name, Factory f) {
        var buf = f.create();
        EnumSet<Flag> src = EnumSet.allOf(Flag.class);
        buf.writeCollection(src, (b, e) -> b.writeVarInt(e.ordinal()));
        assertEquals(src, buf.readCollection(Flag.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumSetEmpty(String name, Factory f) {
        var buf = f.create();
        EnumSet<Flag> src = EnumSet.noneOf(Flag.class);
        buf.writeCollection(src, (b, e) -> b.writeVarInt(e.ordinal()));
        assertEquals(src, buf.readCollection(Flag.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void enumSetGenericReadReturnsEmpty(String name, Factory f) {
        var buf = f.create();
        buf.writeCollection(EnumSet.of(Flag.B), (b, e) -> b.writeVarInt(e.ordinal()));
        // generic read cannot reconstruct enum members — returns empty but keeps stream valid
        var got = buf.readCollection(b -> b.readVarInt(), java.util.ArrayList::new);
        assertTrue(got.isEmpty());
        assertFalse(buf.isReadable());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void optional(String name, Factory f) {
        var buf = f.create();
        buf.writeOptional(STR_VAL, (b, s) -> b.writeString(s));
        buf.writeOptional((String) null, (b, s) -> b.writeString(s));
        assertEquals(Optional.of(STR_VAL), buf.readOptional(b -> b.readString()));
        assertEquals(Optional.empty(),      buf.readOptional(b -> b.readString()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void mixedPayload(String name, Factory f) {
        var buf = f.create();
        buf.writeVarInt(99)
           .writeString("test")
           .writeDouble(Math.PI)
           .writeBoolean(true)
           .writeBoolean(false)
           .writeIntArray(1, 2, 3);

        assertEquals(99,      buf.readVarInt());
        assertEquals("test",  buf.readString());
        assertEquals(Math.PI, buf.readDouble(), 1e-10);
        assertTrue(buf.readBoolean());
        assertFalse(buf.readBoolean());
        assertArrayEquals(new int[]{1, 2, 3}, buf.readIntArray());
    }

    // --- protocol-evolution: append-only field guard (docs/plans/2026-07-30-protocol-evolution.md) ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void hasMore_newWriterOldReader_trailingFieldIgnored(String name, Factory f) {
        var buf = f.create();
        buf.writeVarInt(42);
        buf.writeString("v2 field");   // old reader never reads this

        assertEquals(42, buf.readVarInt());
        assertTrue(buf.hasMore());
        // old reader stops here; trailing bytes are simply left unread and discarded with the buffer.
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void hasMore_oldWriterNewReader_fieldStaysAtDefault(String name, Factory f) {
        var buf = f.create();
        buf.writeVarInt(42);   // v1 writer never wrote the v2 field

        int v1 = buf.readVarInt();
        String v2 = buf.hasMore() ? buf.readString() : "default";

        assertEquals(42, v1);
        assertEquals("default", v2);
        assertFalse(buf.hasMore());
    }
}
