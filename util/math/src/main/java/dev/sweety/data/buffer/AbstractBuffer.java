package dev.sweety.data.buffer;

import dev.sweety.data.HasId;
import dev.sweety.data.buffer.io.AbstractDecoder;
import dev.sweety.data.buffer.io.AbstractEncoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableEncoder;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.math.MathUtils;
import dev.sweety.math.map.Enum2BooleanMap;
import dev.sweety.math.map.Enum2ByteMap;
import dev.sweety.math.map.Enum2CharMap;
import dev.sweety.math.map.Enum2DoubleMap;
import dev.sweety.math.map.Enum2FloatMap;
import dev.sweety.math.map.Enum2IntMap;
import dev.sweety.math.map.Enum2LongMap;
import dev.sweety.math.map.Enum2ShortMap;
import dev.sweety.math.pool.Release;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import dev.sweety.serialization.format.StructuredSink;
import dev.sweety.serialization.format.StructuredSource;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import dev.sweety.data.buffer.adapter.BufferStructuredAdapter;
import dev.sweety.serialization.Reader;
import dev.sweety.serialization.Writer;
import dev.sweety.serialization.format.StructuredSink;
import dev.sweety.serialization.format.StructuredSource;

public abstract class AbstractBuffer<Self extends AbstractBuffer<Self>> implements BufferReader, BufferWriter, PackedBooleanAccessor<Self>, AutoCloseable {
    protected static final int MAX_ARRAY_SIZE = 1 << 23; // 8MB — ForwardData batches exceed 1MB at 3×1500p
    private static final int MAX_STRING_BYTES = 1 << 20;

    private transient StructuredSink structuredSinkView;
    private transient StructuredSource structuredSourceView;

    /**
     * Returns a format-agnostic {@link StructuredSink} view over this buffer.
     */
    public StructuredSink asSink() {
        if (structuredSinkView == null) {
            structuredSinkView = BufferStructuredAdapter.ofSink(this);
        }
        return structuredSinkView;
    }

    /**
     * Returns a format-agnostic {@link StructuredSource} view over this buffer.
     */
    public StructuredSource asSource() {
        if (structuredSourceView == null) {
            structuredSourceView = BufferStructuredAdapter.ofSource(this);
        }
        return structuredSourceView;
    }

    /**
     * Serializes an object into this buffer using the given format-agnostic {@link Writer}.
     */
    public <T> Self writeStructured(Writer<T, StructuredSink> writer, T value) {
        writer.write(asSink(), value);
        //noinspection unchecked
        return (Self) this;
    }

    /**
     * Deserializes an object from this buffer using the given format-agnostic {@link Reader}.
     */
    public <T> T readStructured(Reader<T, StructuredSource> reader) {
        return reader.read(asSource());
    }

    public abstract void clear();

    /**
     * Reset state for pool reuse. Called by the allocator after reclaiming from the pool.
     */
    protected abstract void poolReset();

    public abstract AbstractBuffer<Self> discardReadBytes();

    public abstract int capacity();

    public abstract int writableBytes();

    public abstract AbstractBuffer<Self> ensureWritable(int minWritableBytes);

    public boolean isReadable(int bytes) {
        return readableBytes() >= bytes;
    }

    public abstract AbstractBuffer<Self> writeInt(int value);

    public abstract int readInt();

    private AbstractBuffer<Self> writeVarUnsigned(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        return writeByte((byte) value);
    }

    private long readVarUnsigned(int maxBytes) {
        int numRead = 0;
        long result = 0;
        byte read;

        do {
            read = readByte();
            long value = read & 0x7FL;
            result |= value << (7 * numRead);

            numRead++;
            if (numRead > maxBytes) throw PacketDecodeException.of("VarInt/VarLong too big").runtime();
        } while ((read & 0x80) != 0);

        return result;
    }

    public AbstractBuffer<Self> writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        return writeByte((byte) value);
    }

    public int readVarInt() {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = readByte();
            result |= (read & 0x7F) << (7 * numRead);
            numRead++;
            if (numRead > 5) throw PacketDecodeException.of("VarInt too big").runtime();
        } while ((read & 0x80) != 0);
        return result;
    }

    public AbstractBuffer<Self> writeVarLong(long value) {
        return writeVarUnsigned(value);
    }

    public long readVarLong() {
        return readVarUnsigned(10);
    }

    /**
     * Zig-zag encoded signed varint: {@code writeVarInt} treats its argument as unsigned, so a small
     * negative value (e.g. {@code -1}) would otherwise always cost the full 5 bytes. Zig-zag maps
     * {@code 0,-1,1,-2,2,...} to {@code 0,1,2,3,4,...} first, so small magnitudes of either sign stay
     * cheap — use this instead of {@link #writeVarInt} whenever the value can be negative.
     */
    public AbstractBuffer<Self> writeVarIntZigZag(int value) {
        return writeVarInt((value << 1) ^ (value >> 31));
    }

    public int readVarIntZigZag() {
        int value = readVarInt();
        return (value >>> 1) ^ -(value & 1);
    }

    /** Zig-zag encoded signed varlong — see {@link #writeVarIntZigZag} for why. */
    public AbstractBuffer<Self> writeVarLongZigZag(long value) {
        return writeVarLong((value << 1) ^ (value >> 63));
    }

    public long readVarLongZigZag() {
        long value = readVarLong();
        return (value >>> 1) ^ -(value & 1);
    }

    public abstract AbstractBuffer<Self> writeDouble(double value);

    public abstract double readDouble();

    public abstract AbstractBuffer<Self> writeShort(short value);

    public abstract short readShort();

    public abstract AbstractBuffer<Self> writeByte(byte value);

    public abstract byte readByte();

    private byte writeMask = 0, writeMaskIndex = 0;
    private int writePosIndex = 0;
    private byte readMask = 0, readMaskIndex = 0;

    @Override
    public AbstractBuffer<Self> writeBoolean(boolean value) {
        PackedBooleanAccessor.super.writeBoolean(value);
        return this;
    }

    @Override
    public boolean readBoolean() {
        return PackedBooleanAccessor.super.readBoolean();
    }

    @Override
    public byte writeMask() {
        return writeMask;
    }

    @Override
    public void writeMask(byte writeMask) {
        this.writeMask = writeMask;
    }

    @Override
    public byte writeMaskIndex() {
        return writeMaskIndex;
    }

    @Override
    public void writeMaskIndex(byte writeMaskIndex) {
        this.writeMaskIndex = writeMaskIndex;
    }

    @Override
    public int writePosIndex() {
        return writePosIndex;
    }

    @Override
    public void writePosIndex(int writePosIndex) {
        this.writePosIndex = writePosIndex;
    }

    @Override
    public byte readMask() {
        return readMask;
    }

    @Override
    public void readMask(byte readMask) {
        this.readMask = readMask;
    }

    @Override
    public byte readMaskIndex() {
        return readMaskIndex;
    }

    @Override
    public void readMaskIndex(byte readMaskIndex) {
        this.readMaskIndex = readMaskIndex;
    }

    public abstract AbstractBuffer<Self> setByte(int index, byte value);

    public abstract byte getByte(int index);

    public abstract AbstractBuffer<Self> setShort(int index, short value);

    public abstract short getShort(int index);

    public abstract AbstractBuffer<Self> setInt(int index, int value);

    public abstract int getInt(int index);

    public abstract AbstractBuffer<Self> setLong(int index, long value);

    public abstract long getLong(int index);

    public abstract AbstractBuffer<Self> setFloat(int index, float value);

    public abstract float getFloat(int index);

    public abstract AbstractBuffer<Self> setDouble(int index, double value);

    public abstract double getDouble(int index);

    public abstract AbstractBuffer<Self> setChar(int index, char value);

    public abstract char getChar(int index);

    public abstract boolean isReadable();

    public abstract AbstractBuffer<Self> writeChar(char value);

    public abstract char readChar();

    public abstract AbstractBuffer<Self> writeFloat(float value);

    public abstract float readFloat();

    public abstract AbstractBuffer<Self> writeLong(long value);

    public abstract long readLong();

    public abstract short readUnsignedByte();

    public AbstractBuffer<Self> writeString(String data, Charset charset) {
        if (data == null) return writeVarInt(0);
        byte[] bytes = data.getBytes(charset);
        writeVarInt(bytes.length);
        return writeBytes(bytes);
    }

    public String readString(Charset charset) {
        int length = readBoundedLength("string", MAX_STRING_BYTES);
        requireReadable(length, "string");

        byte[] bytes = new byte[length];
        readBytes(bytes);
        return new String(bytes, charset);
    }

    public AbstractBuffer<Self> writeString(String data) {
        return writeString(data, StandardCharsets.UTF_8);
    }

    public String readString() {
        return readString(StandardCharsets.UTF_8);
    }

    /**
     * ISO-4217 alpha-3 currency code, fixed 3 raw ASCII bytes — no length prefix, since the alphabetic
     * code is always exactly 3 letters by spec. Currency is operator-configurable (env var), not a
     * closed compile-time set, so this stays a raw code rather than a Java enum. Empty string (the
     * existing error/no-currency sentinel) encodes as 3 NUL bytes, since ISO-4217 letters are never 0x00.
     */
    public AbstractBuffer<Self> writeCurrencyCode(String code) {
        if (code == null || code.isBlank()) return writeBytes(new byte[3]);
        byte[] bytes = code.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != 3) throw new IllegalArgumentException("currency code must be 3 letters: " + code);
        return writeBytes(bytes);
    }

    public String readCurrencyCode() {
        byte[] bytes = new byte[3];
        readBytes(bytes);
        if (bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0) return "";
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public AbstractBuffer<Self> writeStringArray(String... array) {
        if (writeNullCheck(array)) return this;
        writeVarInt(array.length);
        for (String i : array) writeString(i);
        return this;
    }

    public String[] readStringArray() {
        if (!readPresence()) return null;
        int len = readBoundedLength("String[]", MAX_ARRAY_SIZE);
        String[] arr = new String[len];
        for (int i = 0; i < len; i++) arr[i] = readString();
        return arr;
    }

    public AbstractBuffer<Self> writeEnum(Enum<?> enumVal) {
        final int val = enumVal instanceof HasId hasId ? hasId.id() : enumVal.ordinal();
        return writeVarInt(val);
    }

    private static final ClassValue<Int2ObjectMap<?>> ENUM_ID_CACHE = new ClassValue<>() {
        @Override
        protected Int2ObjectMap<?> computeValue(Class<?> type) {
            var constants = type.getEnumConstants();
            var map = new Int2ObjectOpenHashMap<>(constants.length);
            for (Object c : constants) map.put(((HasId) c).id(), c);
            return map;
        }
    };

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        int val = this.readVarInt();

        if (HasId.class.isAssignableFrom(clazz)) {
            //noinspection unchecked
            T result = (T) ENUM_ID_CACHE.get(clazz).get(val);
            if (result == null)
                throw PacketDecodeException.of("Invalid enum id: " + val).runtime();
            return result;
        }

        T[] constants = clazz.getEnumConstants();
        if (val >= 0 && val < constants.length) return constants[val];
        throw PacketDecodeException.of("Invalid enum ordinal: " + val).runtime();
    }

    public <T extends Enum<T>, S> AbstractBuffer<Self> writeEnum(T value, Function<T, S> stateMapper, AbstractCallableEncoder<? super S> stateEncoder) {

        stateEncoder.write(this, stateMapper.apply(value));
        return this;
    }

    public <T extends Enum<T>, S> T readEnum(AbstractCallableDecoder<? extends S> stateDecoder, Function<S, T> mapper) {
        return mapper.apply(stateDecoder.read(this));
    }

    public AbstractBuffer<Self> writeUuid(UUID uuid) {
        return writeVarLong(uuid.getMostSignificantBits()).writeVarLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        requireReadable(1, "uuid");
        final long mst = readVarLong();
        requireReadable(1, "uuid");
        return new UUID(mst, readVarLong());
    }

    public AbstractBuffer<Self> writeByteArray(byte... bytes) {
        return writeVarInt(bytes.length).writeBytes(bytes);
    }

    public byte[] readByteArray() {
        int len = readBoundedLength("byte[]", MAX_ARRAY_SIZE);
        requireReadable(len, "byte[]");
        byte[] bytes = new byte[len];
        this.readBytes(bytes);
        return bytes;
    }

    public AbstractBuffer<Self> writeBooleanArray(boolean... array) {
        writeVarInt(array.length);
        for (boolean i : array) writeBoolean(i);
        return this;
    }

    public boolean[] readBooleanArray() {
        int len = readBoundedLength("boolean[]", MAX_ARRAY_SIZE);
        boolean[] arr = new boolean[len];
        for (int i = 0; i < len; i++) arr[i] = readBoolean();
        return arr;
    }

    public AbstractBuffer<Self> writeCharArray(char... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Character.BYTES);
        for (char i : array) writeChar(i);
        return this;
    }

    public char[] readCharArray() {
        int len = readBoundedLength("char[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Character.BYTES, "char[]");
        char[] arr = new char[len];
        for (int i = 0; i < len; i++) arr[i] = readChar();
        return arr;
    }

    public AbstractBuffer<Self> writeIntArray(int... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Integer.BYTES);
        for (int i : array) writeInt(i);
        return this;
    }

    public int[] readIntArray() {
        int len = readBoundedLength("int[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Integer.BYTES, "int[]");
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readInt();
        return arr;
    }

    public AbstractBuffer<Self> writeVarIntArray(int... array) {
        writeVarInt(array.length);
        for (int i : array) writeVarInt(i);
        return this;
    }

    public int[] readVarIntArray() {
        int len = readBoundedLength("varInt[]", MAX_ARRAY_SIZE);
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readVarInt();
        return arr;
    }

    public AbstractBuffer<Self> writeShortArray(short... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Short.BYTES);
        for (short i : array) writeShort(i);
        return this;
    }

    public short[] readShortArray() {
        int len = readBoundedLength("short[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Short.BYTES, "short[]");
        short[] arr = new short[len];
        for (int i = 0; i < len; i++) arr[i] = readShort();
        return arr;
    }

    public AbstractBuffer<Self> writeFloatArray(float... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Float.BYTES);
        for (float i : array) writeFloat(i);
        return this;
    }

    public float[] readFloatArray() {
        int len = readBoundedLength("float[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Float.BYTES, "float[]");
        float[] arr = new float[len];
        for (int i = 0; i < len; i++) arr[i] = readFloat();
        return arr;
    }

    public AbstractBuffer<Self> writeDoubleArray(double... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Double.BYTES);
        for (double i : array) writeDouble(i);
        return this;
    }

    public double[] readDoubleArray() {
        int len = readBoundedLength("double[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Double.BYTES, "double[]");
        double[] arr = new double[len];
        for (int i = 0; i < len; i++) arr[i] = readDouble();
        return arr;
    }

    public AbstractBuffer<Self> writeVarLongArray(long... array) {
        writeVarInt(array.length);
        for (long i : array) writeVarLong(i);
        return this;
    }

    public long[] readVarLongArray() {
        int len = readBoundedLength("varLong[]", MAX_ARRAY_SIZE);
        long[] arr = new long[len];
        for (int i = 0; i < len; i++) arr[i] = readVarLong();
        return arr;
    }

    private AbstractBuffer<Self> writePresence(boolean present) {
        return this.writeBoolean(present);
    }

    private boolean readPresence() {
        return this.readBoolean();
    }

    private <T> boolean writeNullCheck(T object) {
        boolean notNull = object != null;
        writePresence(notNull);
        return !notNull;
    }

    /**
     * Discriminated-union tag for the generic write/read paths (objects, collections, maps):
     * one byte on the wire identifying which shape follows, so the reader knows how to decode it
     * without external type information.
     */
    private enum Kind {
        NULL(0), GENERIC(1), ENUM_BITSET(2), INT2OBJECT(3), LONG2OBJECT(4),
        OBJECT2INT(5), OBJECT2LONG(6), INT_COLLECTION(7), LONG_COLLECTION(8);

        private static final Kind[] BY_ID = values();
        private final byte id;

        Kind(int id) {
            this.id = (byte) id;
        }

        private static Kind fromId(byte id) {
            for (Kind k : BY_ID) if (k.id == id) return k;
            throw PacketDecodeException.of("Unknown kind tag " + id).runtime();
        }
    }

    /** Writes the single-byte kind tag identifying the shape that follows. */
    private void writeKind(Kind kind) {
        writeByte(kind.id);
    }

    /** Reads the single-byte kind tag written by {@link #writeKind}. */
    private Kind readKind() {
        return Kind.fromId(readByte());
    }

    // ── Dynamic (untyped Object) values ─────────────────────────────────────────────────────────
    //
    // Union of tag sets that used to be hand-rolled separately in RpcCodec (SQL RPC cell values),
    // ConfigBlobCodec (config snapshot tree), and this class's own Kind enum above — three copies of
    // the same "byte tag + switch" shape. This is the one canonical set; callers never see the enum,
    // just writeDynamic(Object)/readDynamic().

    private enum DynamicTag {
        NULL(0), BOOLEAN(1), SHORT(2), INT(3), LONG(4), FLOAT(5), DOUBLE(6),
        STRING(7), BYTES(8), UUID(9), TIMESTAMP(10), DECIMAL(11), MAP(12), LIST(13);

        private static final DynamicTag[] BY_ID = values();
        private final byte id;

        DynamicTag(int id) {
            this.id = (byte) id;
        }

        private static DynamicTag fromId(byte id) {
            for (DynamicTag t : BY_ID) if (t.id == id) return t;
            throw PacketDecodeException.of("Unknown dynamic value tag " + id).runtime();
        }
    }

    // Guards against a malicious/corrupt stream nesting MAP-in-LIST-in-MAP deep enough to blow the
    // stack, and against a declared LIST size driving a premature huge ArrayList preallocation before
    // any element is actually read (same bug class as Batch.packetCount).
    private static final int MAX_DYNAMIC_DEPTH = 64;
    private static final int MAX_DYNAMIC_LIST_SIZE = 1 << 20;

    /**
     * Normalizes odd-but-common Java types onto the ones {@link DynamicTag} actually encodes, so
     * callers don't have to pre-convert (mirrors what {@code RpcCodec.normalise} used to do locally).
     */
    private static Object normalizeDynamic(Object value) {
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof java.sql.Date d) return new java.sql.Timestamp(d.getTime());
        if (value instanceof Date d) return new java.sql.Timestamp(d.getTime());
        if (value instanceof java.math.BigInteger bi) return bi.longValue();
        if (value instanceof Number n && !(value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Float
                || value instanceof Short || value instanceof java.math.BigDecimal)) {
            return n.longValue();
        }
        return value;
    }

    private static DynamicTag tagOfDynamic(Object value) {
        return switch (value) {
            case null -> DynamicTag.NULL;
            case Boolean ignored -> DynamicTag.BOOLEAN;
            case Short ignored -> DynamicTag.SHORT;
            case Integer ignored -> DynamicTag.INT;
            case Long ignored -> DynamicTag.LONG;
            case Float ignored -> DynamicTag.FLOAT;
            case Double ignored -> DynamicTag.DOUBLE;
            case String ignored -> DynamicTag.STRING;
            case byte[] ignored -> DynamicTag.BYTES;
            case java.util.UUID ignored -> DynamicTag.UUID;
            case java.sql.Timestamp ignored -> DynamicTag.TIMESTAMP;
            case java.math.BigDecimal ignored -> DynamicTag.DECIMAL;
            case Map<?, ?> ignored -> DynamicTag.MAP;
            case List<?> ignored -> DynamicTag.LIST;
            // Unknown type: fall back to its String form rather than fail the whole write.
            default -> DynamicTag.STRING;
        };
    }

    /** Writes any of the {@link DynamicTag} types (recursing into {@code Map}/{@code List} contents). */
    public AbstractBuffer<Self> writeDynamic(@Nullable Object rawValue) {
        writeDynamicAt(rawValue, 0);
        return this;
    }

    /** Reads a value written by {@link #writeDynamic}. */
    public @Nullable Object readDynamic() {
        return readDynamic(0);
    }

    private Object readDynamic(int depth) {
        DynamicTag tag = DynamicTag.fromId(readByte());
        return switch (tag) {
            case NULL -> null;
            case BOOLEAN -> readBoolean();
            case SHORT -> readShort();
            case INT -> readVarInt();
            case LONG -> readVarLong();
            case FLOAT -> readFloat();
            case DOUBLE -> readDouble();
            case STRING -> readString();
            case BYTES -> readByteArray();
            case UUID -> readUuid();
            case TIMESTAMP -> new java.sql.Timestamp(readVarLong());
            case DECIMAL -> new java.math.BigDecimal(readString());
            case MAP -> readDynamicMap(depth);
            case LIST -> readDynamicList(depth);
        };
    }

    private void writeDynamicMap(Map<String, Object> map, int depth) {
        writeVarInt(map.size());
        for (Map.Entry<String, Object> e : map.entrySet()) {
            writeString(e.getKey());
            writeDynamicAt(e.getValue(), depth);
        }
    }

    private Map<String, Object> readDynamicMap(int depth) {
        if (depth > MAX_DYNAMIC_DEPTH)
            throw PacketDecodeException.of("dynamic value nesting too deep (max " + MAX_DYNAMIC_DEPTH + ")").runtime();
        int n = readVarInt();
        Map<String, Object> map = new TreeMap<>();
        for (int i = 0; i < n; i++) map.put(readString(), readDynamic(depth + 1));
        return map;
    }

    private void writeDynamicList(List<Object> list, int depth) {
        writeVarInt(list.size());
        for (Object o : list) writeDynamicAt(o, depth);
    }

    private List<Object> readDynamicList(int depth) {
        if (depth > MAX_DYNAMIC_DEPTH)
            throw PacketDecodeException.of("dynamic value nesting too deep (max " + MAX_DYNAMIC_DEPTH + ")").runtime();
        int n = readVarInt();
        if (n < 0 || n > MAX_DYNAMIC_LIST_SIZE)
            throw PacketDecodeException.of("dynamic value list size out of bounds: " + n).runtime();
        List<Object> list = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) list.add(readDynamic(depth + 1));
        return list;
    }

    @SuppressWarnings("unchecked")
    private void writeDynamicAt(@Nullable Object rawValue, int depth) {
        if (depth > MAX_DYNAMIC_DEPTH)
            throw PacketDecodeException.of("dynamic value nesting too deep (max " + MAX_DYNAMIC_DEPTH + ")").runtime();
        Object value = rawValue == null ? null : normalizeDynamic(rawValue);
        DynamicTag tag = tagOfDynamic(value);
        writeByte(tag.id);
        switch (tag) {
            case NULL -> {}
            case BOOLEAN -> writeBoolean((Boolean) value);
            case SHORT -> writeShort((Short) value);
            case INT -> writeVarInt((Integer) value);
            case LONG -> writeVarLong((Long) value);
            case FLOAT -> writeFloat((Float) value);
            case DOUBLE -> writeDouble((Double) value);
            case STRING -> writeString(value instanceof String s ? s : String.valueOf(value));
            case BYTES -> writeByteArray((byte[]) value);
            case UUID -> writeUuid((java.util.UUID) value);
            case TIMESTAMP -> writeVarLong(((java.sql.Timestamp) value).getTime());
            case DECIMAL -> writeString(((java.math.BigDecimal) value).toPlainString());
            case MAP -> writeDynamicMap((Map<String, Object>) value, depth + 1);
            case LIST -> writeDynamicList((List<Object>) value, depth + 1);
        }
    }

    /**
     * Consumes a kind-2 bitset ({@code words} longs) from the stream and returns the number of
     * set bits. Used by generic read methods that lack the enum {@link Class} to decode the keys/members
     * but still need to advance the reader past the bitset so the stream stays valid.
     */
    private int consumeEnumBitsetAndCountBits() {
        int words = readVarInt();
        int count = 0;
        for (int i = 0; i < words; i++) count += Long.bitCount(readVarLong());
        return count;
    }

    /**
     * Writes an EnumMap body for kind-2 encoding: a long[] presence bitset followed by values
     * in ordinal order (only for keys present in the map). Derives the enum universe from the
     * first key; for an empty map writes {@code words = 0} and no values.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <V> void writeEnumMapBody(Map<?, V> map, AbstractCallableEncoder<? super V> vEncoder) {
        if (map.isEmpty()) {
            writeVarInt(0);
            return;
        }
        Enum<?> first = (Enum<?>) map.keySet().iterator().next();
        Enum<?>[] universe = (Enum<?>[]) first.getDeclaringClass().getEnumConstants();
        int words = (universe.length + 63) >>> 6;
        long[] bits = new long[words];
        for (Enum<?> e : universe) {
            if (map.containsKey(e)) {
                int ord = e.ordinal();
                bits[ord >>> 6] |= 1L << (ord & 63);
            }
        }
        writeVarInt(words);
        for (long w : bits) writeVarLong(w);

        for (Enum<?> e : universe) {
            if (map.containsKey(e)) vEncoder.write(this, map.get(e));
        }
    }

    /**
     * Writes an EnumSet body for kind-2 encoding: a long[] membership bitset.
     * Derives the enum universe from the first element; for an empty set writes {@code words = 0}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeEnumSetBody(EnumSet<?> set) {
        if (set.isEmpty()) {
            writeVarInt(0);
            return;
        }
        Enum<?> first = set.iterator().next();
        Enum<?>[] universe = (Enum<?>[]) first.getDeclaringClass().getEnumConstants();
        int words = (universe.length + 63) >>> 6;
        long[] bits = new long[words];
        for (Enum<?> e : set) {
            int ord = e.ordinal();
            bits[ord >>> 6] |= 1L << (ord & 63);
        }
        writeVarInt(words);
        for (long w : bits) writeVarLong(w);
    }

    /**
     * Writes a single presence marker then, if present, the payload. Used by {@link #writeObject}
     * and {@link #writeOptional} so both share the same wire layout without chaining public overloads.
     */
    private <T> AbstractBuffer<Self> writeMarkedPayload(boolean present, @Nullable T valueIfPresent, AbstractCallableEncoder<? super T> encoder) {
        writePresence(present);

        if (!present) return this;
        encoder.write(this, valueIfPresent);
        return this;
    }

    /**
     * Reads a presence marker; if absent returns {@code null}, otherwise decodes the payload.
     */
    private <T> @Nullable T readMarkedPayload(AbstractCallableDecoder<? extends T> decoder) {
        if (!readPresence()) return null;
        return decoder.read(this);
    }

    public <T> AbstractBuffer<Self> writeObject(@Nullable T object, AbstractCallableEncoder<? super T> encoder) {
        return writeMarkedPayload(object != null, object, encoder);
    }

    public <T> T readObject(AbstractCallableDecoder<? extends T> decoder) {
        return readMarkedPayload(decoder);
    }

    public <T> Optional<T> readOptional(AbstractCallableDecoder<? extends T> decoder) {
        return Optional.ofNullable(readMarkedPayload(decoder));
    }

    public <T extends AbstractDecoder> Optional<T> readOptional(Supplier<T> factory) {
        return Optional.ofNullable(readMarkedPayload(buffer -> {
            T obj = factory.get();
            obj.read(buffer);
            return obj;
        }));
    }

    public <T> AbstractBuffer<Self> writeOptional(@Nullable T value, AbstractCallableEncoder<? super T> encoder) {
        return writeMarkedPayload(value != null, value, encoder);
    }

    public <T extends AbstractEncoder> AbstractBuffer<Self> writeOptional(@Nullable T value) {
        return writeOptional(value, (buffer, t) -> t.write(buffer));
    }

    public <T extends AbstractEncoder> AbstractBuffer<Self> writeObject(T object) {
        return writeObject(object, (buffer, t) -> t.write(buffer));
    }

    public <T extends AbstractDecoder> T readObject(Supplier<T> factory) {
        return readObject(buffer -> {
            T obj = factory.get();
            obj.read(buffer);
            return obj;
        });
    }

    public <T> AbstractBuffer<Self> writeIterable(Iterable<T> iterable, int size, AbstractCallableEncoder<? super T> encoder) {
        if (writeNullCheck(iterable)) return this;
        writeVarInt(size);

        for (T entry : iterable) encoder.write(this, entry);
        return this;
    }

    @SafeVarargs
    public final <T> AbstractBuffer<Self> writeArray(AbstractCallableEncoder<? super T> encoder, T... array) {
        if (writeNullCheck(array)) return this;
        writeVarInt(array.length);
        for (T entry : array) encoder.write(this, entry);
        return this;
    }

    public <T> AbstractBuffer<Self> writeCollection(Collection<T> collection, AbstractCallableEncoder<? super T> encoder) {
        if (collection == null) {
            writeKind(Kind.NULL);
            return this;
        }
        if (collection instanceof EnumSet<?> enumSet) {
            writeKind(Kind.ENUM_BITSET);
            writeEnumSetBody(enumSet);
            return this;
        }
        if (collection instanceof IntCollection ic) {
            writeKind(Kind.INT_COLLECTION);
            writeVarInt(ic.size());
            var it = ic.intIterator();
            while (it.hasNext()) writeVarInt(it.nextInt());
            return this;
        }
        if (collection instanceof LongCollection lc) {
            writeKind(Kind.LONG_COLLECTION);
            writeVarInt(lc.size());
            var it = lc.longIterator();
            while (it.hasNext()) writeVarLong(it.nextLong());
            return this;
        }
        writeKind(Kind.GENERIC);
        writeVarInt(collection.size());

        for (T entry : collection) encoder.write(this, entry);
        return this;
    }

    public <T extends AbstractEncoder> AbstractBuffer<Self> writeCollection(Collection<T> collection) {
        return writeCollection(collection, (buffer, t) -> t.write(buffer));
    }

    public <T, C extends Collection<T>> C readCollection(AbstractCallableDecoder<? extends T> decoder, IntFunction<C> collectionFactory) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        // kind=2: EnumSet bitset encoding — elements have no per-element bytes so decoder cannot
        // be called. Consume the bitset to keep the stream valid and return an empty collection.
        // Use readCollection(Class) to get the actual members.
        if (kind == Kind.ENUM_BITSET) {
            consumeEnumBitsetAndCountBits();
            return collectionFactory.apply(0);
        }
        if (kind == Kind.INT_COLLECTION) {
            int size = readBoundedLength("intCollection", MAX_ARRAY_SIZE);
            C collection = collectionFactory.apply(size);
            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked")
                T elem = (T) (Integer) readVarInt();
                collection.add(elem);
            }
            return collection;
        }
        if (kind == Kind.LONG_COLLECTION) {
            int size = readBoundedLength("longCollection", MAX_ARRAY_SIZE);
            C collection = collectionFactory.apply(size);
            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked")
                T elem = (T) (Long) readVarLong();
                collection.add(elem);
            }
            return collection;
        }
        final int size = readBoundedLength("collection", MAX_ARRAY_SIZE);
        final C collection = collectionFactory.apply(size);

        for (int i = 0; i < size; i++) collection.add(decoder.read(this));
        return collection;
    }

    /**
     * Decodes an {@link EnumSet} written by {@link #writeCollection(Collection, AbstractCallableEncoder)}
     * (or {@link #writeEnumSet}) using the compact bitset encoding (kind tag {@code 2}).
     * Also accepts kind {@code 1} (generic element-by-element) as a fallback for interop with
     * callers that wrote the set through a non-enum-aware path.
     */
    public <E extends Enum<E>> EnumSet<E> readCollection(Class<E> type) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        E[] universe = type.getEnumConstants();
        EnumSet<E> set = EnumSet.noneOf(type);
        if (kind == Kind.ENUM_BITSET) {
            int words = readVarInt();
            long[] bits = new long[words];
            for (int i = 0; i < words; i++) bits[i] = readVarLong();
            for (E e : universe) {
                int ord = e.ordinal();
                if ((ord >>> 6) < words && (bits[ord >>> 6] & (1L << (ord & 63))) != 0) set.add(e);
            }
        } else if (kind == Kind.GENERIC) {
            int size = readBoundedLength("enumSet", MAX_ARRAY_SIZE);
            for (int i = 0; i < size; i++) set.add(readEnum(type));
        } else {
            throw PacketDecodeException.of("Unknown collection kind: " + kind).runtime();
        }
        return set;
    }

    public <T> List<T> readList(AbstractCallableDecoder<? extends T> decoder) {
        return readCollection(decoder, ArrayList::new);
    }

    public <T> T[] readArray(AbstractCallableDecoder<? extends T> decoder, IntFunction<T[]> arrayFactory) {
        if (!readPresence()) return null;
        int size = readBoundedLength("array", MAX_ARRAY_SIZE);
        T[] array = arrayFactory.apply(size);

        for (int i = 0; i < size; i++) array[i] = decoder.read(this);
        return array;
    }

    public <T extends AbstractDecoder> List<T> readCollection(Supplier<T> factory) {
        return readList(buffer -> buffer.readObject(factory));
    }

    /**
     * @deprecated Use {@link #writeMap(Map, AbstractCallableEncoder, AbstractCallableEncoder)} to avoid per-entry Pair allocation.
     */
    @Deprecated
    public <K, V> AbstractBuffer<Self> writeMap(Map<K, V> map, AbstractCallableEncoder<Pair<K, V>> encoder) {
        if (map == null) {
            writeKind(Kind.NULL);
            return this;
        }
        writeKind(Kind.GENERIC);
        writeVarInt(map.size());

        for (Map.Entry<K, V> entry : map.entrySet()) encoder.write(this, Pair.of(entry.getKey(), entry.getValue()));
        return this;
    }

    /**
     * @deprecated Use {@link #readMap(AbstractCallableDecoder, AbstractCallableDecoder, IntFunction)} to avoid per-entry Pair allocation.
     */
    @Deprecated
    public <K, V> Map<K, V> readMap(AbstractCallableDecoder<Pair<K, V>> decoder, IntFunction<Map<K, V>> mapFactory) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind == Kind.ENUM_BITSET)
            throw PacketDecodeException.of("Enum-keyed map: use readMap(Class, vDecoder) to decode").runtime();
        int size = readBoundedLength("map", MAX_ARRAY_SIZE);
        Map<K, V> map = mapFactory.apply(size);

        for (int i = 0; i < size; i++) {
            Pair<K, V> pair = decoder.read(this);
            map.put(pair.key(), pair.value());
        }
        return map;
    }

    /**
     * Writes the map with a kind tag. If {@code map} is an {@link EnumMap}, emits kind {@code 2}
     * (compact bitset + values in ordinal order) and the {@code kEncoder} is ignored.
     * Otherwise emits kind {@code 1} (generic entry-by-entry encoding).
     */
    @SuppressWarnings("unchecked")
    public <K, V> AbstractBuffer<Self> writeMap(Map<K, V> map, AbstractCallableEncoder<? super K> kEncoder, AbstractCallableEncoder<? super V> vEncoder) {
        switch (map) {
            case null -> {
                writeKind(Kind.NULL);
                return this;
            }
            case EnumMap<?, ?> enumMap -> {
                writeKind(Kind.ENUM_BITSET);
                writeEnumMapBody(map, vEncoder);
                return this;
            }
            // Any other enum-keyed Map (Enum2IntMap, Enum2LongMap, ... in dev.sweety.math.map — they
            // implement plain Map, not java.util.EnumMap, so they'd otherwise miss the case above and
            // fall through to the generic kind=1 path below, boxing every key). Content-checked since
            // there's no marker type; an empty instance falls through to kind=1 harmlessly (equally
            // cheap: one byte either way for zero entries).
            case Map<?, ?> m when !m.isEmpty() && m.keySet().iterator().next() instanceof Enum<?> -> {
                writeKind(Kind.ENUM_BITSET);
                writeEnumMapBody(map, vEncoder);
                return this;
            }
            case Int2ObjectMap<?> int2ObjectMap -> {
                writeKind(Kind.INT2OBJECT);
                Int2ObjectMap<V> m = (Int2ObjectMap<V>) map;
                writeVarInt(m.size());

                for (Int2ObjectMap.Entry<V> e : m.int2ObjectEntrySet()) {
                    writeVarInt(e.getIntKey());
                    vEncoder.write(this, e.getValue());
                }
                return this;
            }
            case Long2ObjectMap<?> long2ObjectMap -> {
                writeKind(Kind.LONG2OBJECT);
                Long2ObjectMap<V> m = (Long2ObjectMap<V>) map;
                writeVarInt(m.size());

                for (Long2ObjectMap.Entry<V> e : m.long2ObjectEntrySet()) {
                    writeVarLong(e.getLongKey());
                    vEncoder.write(this, e.getValue());
                }
                return this;
            }
            case Object2IntMap<?> object2IntMap -> {
                writeKind(Kind.OBJECT2INT);
                Object2IntMap<K> m = (Object2IntMap<K>) map;
                writeVarInt(m.size());

                for (Object2IntMap.Entry<K> e : m.object2IntEntrySet()) {
                    kEncoder.write(this, e.getKey());
                    writeVarInt(e.getIntValue());
                }
                return this;
            }
            case Object2LongMap<?> object2LongMap -> {
                writeKind(Kind.OBJECT2LONG);
                Object2LongMap<K> m = (Object2LongMap<K>) map;
                writeVarInt(m.size());

                for (Object2LongMap.Entry<K> e : m.object2LongEntrySet()) {
                    kEncoder.write(this, e.getKey());
                    writeVarLong(e.getLongValue());
                }
                return this;
            }
            default -> {
            }
        }
        writeKind(Kind.GENERIC);
        writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            kEncoder.write(this, entry.getKey());
            vEncoder.write(this, entry.getValue());
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> readMap(AbstractCallableDecoder<K> kDecoder, AbstractCallableDecoder<V> vDecoder, IntFunction<Map<K, V>> mapFactory) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        // kind=2: EnumMap bitset encoding — keys are ordinals in the bitset (no key bytes in stream),
        // values follow the bitset. Consume both to keep the stream valid and return an empty map.
        // Use readMap(Class, vDecoder) to reconstruct the actual entries.
        if (kind == Kind.ENUM_BITSET) {
            int count = consumeEnumBitsetAndCountBits();

            for (int i = 0; i < count; i++) vDecoder.read(this);
            return mapFactory.apply(0);
        }
        if (kind == Kind.INT2OBJECT) {
            int size = readBoundedLength("int2ObjectMap", MAX_ARRAY_SIZE);
            Map<K, V> map = mapFactory.apply(size);

            for (int i = 0; i < size; i++) {
                K key = (K) (Integer) readVarInt();
                map.put(key, vDecoder.read(this));
            }
            return map;
        }
        if (kind == Kind.LONG2OBJECT) {
            int size = readBoundedLength("long2ObjectMap", MAX_ARRAY_SIZE);
            Map<K, V> map = mapFactory.apply(size);

            for (int i = 0; i < size; i++) {
                K key = (K) (Long) readVarLong();
                map.put(key, vDecoder.read(this));
            }
            return map;
        }
        if (kind == Kind.OBJECT2INT) {
            int size = readBoundedLength("object2IntMap", MAX_ARRAY_SIZE);
            Map<K, V> map = mapFactory.apply(size);

            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked") V val = (V) (Integer) readVarInt();
                map.put(kDecoder.read(this), val);
            }
            return map;
        }
        if (kind == Kind.OBJECT2LONG) {
            int size = readBoundedLength("object2LongMap", MAX_ARRAY_SIZE);
            Map<K, V> map = mapFactory.apply(size);

            for (int i = 0; i < size; i++) {
                @SuppressWarnings("unchecked") V val = (V) (Long) readVarLong();
                map.put(kDecoder.read(this), val);
            }
            return map;
        }
        int size = readBoundedLength("map", MAX_ARRAY_SIZE);
        Map<K, V> map = mapFactory.apply(size);
        for (int i = 0; i < size; i++) {
            K key = kDecoder.read(this);
            V val = vDecoder.read(this);
            map.put(key, val);
        }
        return map;
    }

    /**
     * Decodes an {@link EnumMap} written by {@link #writeMap(Map, AbstractCallableEncoder, AbstractCallableEncoder)}
     * (or {@link #writeEnumMap}) using the compact bitset encoding (kind tag {@code 2}).
     * Also accepts kind {@code 1} (generic entry-by-entry) as a fallback for interop with callers
     * that wrote the map through a non-enum-aware path.
     */
    public <K extends Enum<K>, V> EnumMap<K, V> readMap(Class<K> keyClass, AbstractCallableDecoder<V> vDecoder) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        EnumMap<K, V> map = new EnumMap<>(keyClass);
        if (kind == Kind.ENUM_BITSET) {
            K[] universe = keyClass.getEnumConstants();
            int words = readVarInt();
            long[] bits = new long[words];
            for (int i = 0; i < words; i++) bits[i] = readVarLong();

            for (K e : universe) {
                int ord = e.ordinal();
                if ((ord >>> 6) < words && (bits[ord >>> 6] & (1L << (ord & 63))) != 0) {
                    map.put(e, vDecoder.read(this));
                }
            }
        } else if (kind == Kind.GENERIC) {
            int size = readBoundedLength("enumMap", MAX_ARRAY_SIZE);

            for (int i = 0; i < size; i++) map.put(readEnum(keyClass), vDecoder.read(this));
        } else {
            throw PacketDecodeException.of("Unknown map kind: " + kind).runtime();
        }
        return map;
    }

    /**
     * Shared reconstruction path for {@link #readEnum2IntMap} and friends — same kind=2 bitset /
     * kind=1 generic dispatch as {@link #readEnumMap(Class, AbstractCallableDecoder)}, but building
     * into whatever primitive enum-map type {@code factory} produces instead of {@link EnumMap}.
     */
    private <E extends Enum<E>, V, M extends Map<E, V>> M readEnumLikeMap(Class<E> keyClass, AbstractCallableDecoder<V> vDecoder, Function<Class<E>, M> factory) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        M map = factory.apply(keyClass);
        if (kind == Kind.ENUM_BITSET) {
            E[] universe = keyClass.getEnumConstants();
            int words = readVarInt();
            long[] bits = new long[words];
            for (int i = 0; i < words; i++) bits[i] = readVarLong();

            for (E e : universe) {
                int ord = e.ordinal();
                if ((ord >>> 6) < words && (bits[ord >>> 6] & (1L << (ord & 63))) != 0) {
                    map.put(e, vDecoder.read(this));
                }
            }
        } else if (kind == Kind.GENERIC) {
            int size = readBoundedLength("enumMap", MAX_ARRAY_SIZE);
            for (int i = 0; i < size; i++) map.put(readEnum(keyClass), vDecoder.read(this));
        } else {
            throw PacketDecodeException.of("Unknown map kind: " + kind).runtime();
        }
        return map;
    }

    public <E extends Enum<E>> Enum2IntMap<E> readEnum2IntMap(Class<E> keyClass, AbstractCallableDecoder<Integer> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2IntMap::of);
    }

    public <E extends Enum<E>> Enum2LongMap<E> readEnum2LongMap(Class<E> keyClass, AbstractCallableDecoder<Long> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2LongMap::of);
    }

    public <E extends Enum<E>> Enum2BooleanMap<E> readEnum2BooleanMap(Class<E> keyClass, AbstractCallableDecoder<Boolean> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2BooleanMap::of);
    }

    public <E extends Enum<E>> Enum2ByteMap<E> readEnum2ByteMap(Class<E> keyClass, AbstractCallableDecoder<Byte> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2ByteMap::of);
    }

    public <E extends Enum<E>> Enum2CharMap<E> readEnum2CharMap(Class<E> keyClass, AbstractCallableDecoder<Character> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2CharMap::of);
    }

    public <E extends Enum<E>> Enum2DoubleMap<E> readEnum2DoubleMap(Class<E> keyClass, AbstractCallableDecoder<Double> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2DoubleMap::of);
    }

    public <E extends Enum<E>> Enum2FloatMap<E> readEnum2FloatMap(Class<E> keyClass, AbstractCallableDecoder<Float> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2FloatMap::of);
    }

    public <E extends Enum<E>> Enum2ShortMap<E> readEnum2ShortMap(Class<E> keyClass, AbstractCallableDecoder<Short> vDecoder) {
        return readEnumLikeMap(keyClass, vDecoder, Enum2ShortMap::of);
    }

    /**
     * Thin alias; delegates to {@link #writeMap} which auto-detects {@link EnumMap} and emits kind {@code 2}.
     */
    public <K extends Enum<K>, V> AbstractBuffer<Self> writeEnumMap(EnumMap<K, V> map, AbstractCallableEncoder<? super V> vEncoder) {
        return writeMap(map, BufferWriter::writeEnum, vEncoder);
    }

    /** Thin alias; {@link #writeMap} already auto-detects any enum-keyed {@link Map} (incl. {@code Enum2IntMap} and friends) and emits kind {@code 2}. */
    public <K extends Enum<K>, V> AbstractBuffer<Self> writeEnum2Map(Map<K, V> map, AbstractCallableEncoder<? super V> vEncoder) {
        return writeMap(map, BufferWriter::writeEnum, vEncoder);
    }

    @Override
    public <K extends AbstractEncoder, V extends AbstractEncoder> BufferWriter writeMap(Map<K, V> map) {
        return writeMap(map, (buffer, k) -> k.write(buffer), (buffer, v) -> v.write(buffer));
    }

    /**
     * Thin alias; delegates to {@link #readMap(Class, AbstractCallableDecoder)}.
     */
    public <K extends Enum<K>, V> EnumMap<K, V> readEnumMap(Class<K> keyClass, AbstractCallableDecoder<V> vDecoder) {
        return readMap(keyClass, vDecoder);
    }

    /**
     * Thin alias; delegates to {@link #writeCollection} which auto-detects {@link EnumSet} and emits kind {@code 2}.
     */
    public <E extends Enum<E>> AbstractBuffer<Self> writeEnumSet(EnumSet<E> set, Class<E> type) {
        if (set == null) {
            writeKind(Kind.NULL);
            return this;
        }
        writeKind(Kind.ENUM_BITSET);
        writeEnumSetBody(set);
        return this;
    }

    /**
     * Thin alias; delegates to {@link #readCollection(Class)}.
     */
    public <E extends Enum<E>> EnumSet<E> readEnumSet(Class<E> type) {
        return readCollection(type);
    }

    public <V> Int2ObjectOpenHashMap<V> readInt2ObjectMap(AbstractCallableDecoder<V> vDecoder) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.INT2OBJECT) throw PacketDecodeException.of("Expected int2ObjectMap (kind 3), got: " + kind).runtime();
        int size = readBoundedLength("int2ObjectMap", MAX_ARRAY_SIZE);
        Int2ObjectOpenHashMap<V> map = new Int2ObjectOpenHashMap<>(size);

        for (int i = 0; i < size; i++) map.put(readVarInt(), vDecoder.read(this));
        return map;
    }

    public <V> Long2ObjectOpenHashMap<V> readLong2ObjectMap(AbstractCallableDecoder<V> vDecoder) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.LONG2OBJECT) throw PacketDecodeException.of("Expected long2ObjectMap (kind 4), got: " + kind).runtime();
        int size = readBoundedLength("long2ObjectMap", MAX_ARRAY_SIZE);
        Long2ObjectOpenHashMap<V> map = new Long2ObjectOpenHashMap<>(size);

        for (int i = 0; i < size; i++) map.put(readVarLong(), vDecoder.read(this));
        return map;
    }

    public <K> Object2IntOpenHashMap<K> readObject2IntMap(AbstractCallableDecoder<K> kDecoder) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.OBJECT2INT) throw PacketDecodeException.of("Expected object2IntMap (kind 5), got: " + kind).runtime();
        int size = readBoundedLength("object2IntMap", MAX_ARRAY_SIZE);
        Object2IntOpenHashMap<K> map = new Object2IntOpenHashMap<>(size);

        for (int i = 0; i < size; i++) map.put(kDecoder.read(this), readVarInt());
        return map;
    }

    public <K> Object2LongOpenHashMap<K> readObject2LongMap(AbstractCallableDecoder<K> kDecoder) {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.OBJECT2LONG) throw PacketDecodeException.of("Expected object2LongMap (kind 6), got: " + kind).runtime();
        int size = readBoundedLength("object2LongMap", MAX_ARRAY_SIZE);
        Object2LongOpenHashMap<K> map = new Object2LongOpenHashMap<>(size);

        for (int i = 0; i < size; i++) map.put(kDecoder.read(this), readVarLong());
        return map;
    }

    public IntOpenHashSet readIntSet() {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.INT_COLLECTION) throw PacketDecodeException.of("Expected intCollection (kind 7), got: " + kind).runtime();
        int size = readBoundedLength("intSet", MAX_ARRAY_SIZE);
        IntOpenHashSet set = new IntOpenHashSet(size);
        for (int i = 0; i < size; i++) set.add(readVarInt());
        return set;
    }

    public IntArrayList readIntList() {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.INT_COLLECTION) throw PacketDecodeException.of("Expected intCollection (kind 7), got: " + kind).runtime();
        int size = readBoundedLength("intList", MAX_ARRAY_SIZE);
        IntArrayList list = new IntArrayList(size);
        for (int i = 0; i < size; i++) list.add(readVarInt());
        return list;
    }

    public LongOpenHashSet readLongSet() {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.LONG_COLLECTION) throw PacketDecodeException.of("Expected longCollection (kind 8), got: " + kind).runtime();
        int size = readBoundedLength("longSet", MAX_ARRAY_SIZE);
        LongOpenHashSet set = new LongOpenHashSet(size);
        for (int i = 0; i < size; i++) set.add(readVarLong());
        return set;
    }

    public LongArrayList readLongList() {
        Kind kind = readKind();
        if (kind == Kind.NULL) return null;
        if (kind != Kind.LONG_COLLECTION) throw PacketDecodeException.of("Expected longCollection (kind 8), got: " + kind).runtime();
        int size = readBoundedLength("longList", MAX_ARRAY_SIZE);
        LongArrayList list = new LongArrayList(size);
        for (int i = 0; i < size; i++) list.add(readVarLong());
        return list;
    }

    public abstract boolean release();

    /**
     * Delegates to {@link #release()} for try-with-resources support.
     */
    @Release
    @Override
    public void close() {
        release();
    }

    public abstract AbstractBuffer<Self> retain(int increment);

    public abstract AbstractBuffer<Self> retain();

    public abstract int refCnt();

    public byte[] getBytes() {
        byte[] bytes = new byte[readableBytes()];
        this.getBytes(this.readerIndex(), bytes);
        return bytes;
    }

    protected abstract void getBytes(int index, byte[] dst);

    public byte[] readAllBytes() {
        byte[] bytes = new byte[readableBytes()];
        readBytes(bytes);
        return bytes;
    }

    public abstract int readableBytes();

    public abstract AbstractBuffer<Self> resetReaderIndex();

    public abstract AbstractBuffer<Self> markReaderIndex();

    public abstract int readerIndex();

    public abstract AbstractBuffer<Self> readerIndex(int readerIndex);

    public abstract AbstractBuffer<Self> resetWriterIndex();

    public abstract AbstractBuffer<Self> markWriterIndex();

    public abstract int writerIndex();

    public abstract AbstractBuffer<Self> writerIndex(int writerIndex);

    public abstract AbstractBuffer<Self> readBytes(byte[] data);

    public abstract AbstractBuffer<Self> readBytes(byte[] data, int offset, int length);

    public abstract AbstractBuffer<Self> writeBytes(byte[] data);

    public abstract AbstractBuffer<Self> writeBytes(byte[] data, int offset, int length);

    public AbstractBuffer<Self> wrapData(AbstractEncoder encoder) {
        byte[] bytes = readAllBytes();
        encoder.write(this);
        return writeBytes(bytes);
    }

    // Prefer zero-copy when possible
    public abstract AbstractBuffer<Self> writeBuffer(Self other);

    public abstract AbstractBuffer<Self> readSlice(int length);

    // Return a retained slice to safely pass across components without immediate copy
    public abstract AbstractBuffer<Self> readRetainedSlice(int length);

    public abstract AbstractBuffer<Self> slice();

    // Create a slice view from current readerIndex with specified length
    public abstract AbstractBuffer<Self> slice(int index, int length);

    // Retained slice for longer-lived sharing
    public abstract AbstractBuffer<Self> retainedSlice(int index, int length);

    public AbstractBuffer<Self> writePercentual(double percent, double scale) {
        return this.writeVarInt((int) (MathUtils.clamp(percent) * scale));
    }

    public double readPercentual(double scale) {
        return this.readVarInt() / scale;
    }

    private static long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) |
                ((long) (z & 0x3FFFFFF) << 12) |
                ((long) (y & 0xFFF));
    }

    public static int posX(long packed) {
        return (int) (packed >> 38);
    }

    public static int posY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int posZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    public AbstractBuffer<Self> writePosition(int x, int y, int z) {
        return writeLong(packPosition(x, y, z));
    }

    public long readPackedPosition() {
        return readLong();
    }

    /**
     * @deprecated Use {@link #readPackedPosition()} + {@link #posX}/{@link #posY}/{@link #posZ} to avoid int[] allocation.
     */
    @Deprecated
    public int[] readPosition() {
        long val = readLong();
        return new int[]{posX(val), posY(val), posZ(val)};
    }

    public AbstractBuffer<Self> writeVarPosition(int x, int y, int z) {
        return writeVarLong(packPosition(x, y, z));
    }

    public long readPackedVarPosition() {
        return readVarLong();
    }

    /**
     * @deprecated Use {@link #readPackedVarPosition()} + {@link #posX}/{@link #posY}/{@link #posZ} to avoid int[] allocation.
     */
    @Deprecated
    public int[] readVarPosition() {
        long val = readVarLong();
        return new int[]{posX(val), posY(val), posZ(val)};
    }

    /**
     * Delta-encodes a position against a known previous position (e.g. the same entity's last sent
     * position) instead of packing it absolute. Each axis is zig-zag varint'd independently, so small
     * per-tick movement collapses to ~1 byte/axis instead of the fixed packed-long width. Caller
     * supplies the previous position (typically tracked per-entity/per-connection); a first-ever
     * position for that entity should just use {@link #writeVarPosition} instead.
     */
    public AbstractBuffer<Self> writePositionDelta(int x, int y, int z, int prevX, int prevY, int prevZ) {
        writeVarIntZigZag(x - prevX);
        writeVarIntZigZag(y - prevY);
        writeVarIntZigZag(z - prevZ);
        return this;
    }

    /** Reads a position written by {@link #writePositionDelta}, reconstructed against the same previous position the writer used. */
    public long readPositionDelta(int prevX, int prevY, int prevZ) {
        int x = prevX + readVarIntZigZag();
        int y = prevY + readVarIntZigZag();
        int z = prevZ + readVarIntZigZag();
        return packPosition(x, y, z);
    }

    public AbstractBuffer<Self> writeFixedInt(double value, int fractionBits) {
        int fixed = (int) Math.round(value * (1 << fractionBits));
        return writeInt(fixed);
    }

    public double readFixedInt(int fractionBits) {
        return readInt() / (double) (1 << fractionBits);
    }

    public AbstractBuffer<Self> writeFixedVarInt(double value, int fractionBits) {
        int fixed = (int) Math.round(value * (1 << fractionBits));
        return writeVarInt(fixed);
    }

    public double readFixedVarInt(int fractionBits) {
        return readVarInt() / (double) (1 << fractionBits);
    }

    public AbstractBuffer<Self> writeFixedVarLong(double value, int fractionBits) {
        long fixed = Math.round(value * (1L << fractionBits));
        return writeVarLong(fixed);
    }

    public double readFixedVarLong(int fractionBits) {
        return readVarLong() / (double) (1L << fractionBits);
    }

    public AbstractBuffer<Self> writeFixedPosition(double x, double y, double z, int fractionBits) {
        return writeFixedVarLong(x, fractionBits)
                .writeFixedVarLong(y, fractionBits)
                .writeFixedVarLong(z, fractionBits);
    }

    public double[] readFixedPosition(int fractionBits) {
        return new double[]{
                readFixedVarLong(fractionBits),
                readFixedVarLong(fractionBits),
                readFixedVarLong(fractionBits)
        };
    }

    protected int readBoundedLength(String label, int max) {
        final int len = readVarInt();
        if (len < 0 || len > max) {
            throw PacketDecodeException.of(label + " length out of bounds: " + len).runtime();
        }
        return len;
    }

    protected void requireReadable(long bytes, String label) {
        if (bytes < 0 || bytes > Integer.MAX_VALUE) {
            throw PacketDecodeException.of("Invalid byte length for " + label + ": " + bytes).runtime();
        }
        if (this.readableBytes() < (int) bytes) {
            throw PacketDecodeException.of("Not enough bytes for " + label + ": requested=" + bytes
                    + ", available=" + this.readableBytes()).runtime();
        }
    }

    @Override
    public String toString() {
        return "%s(ridx=%d, widx=%d, readable=%d)".formatted(getClass().getSimpleName(), readerIndex(), writerIndex(), readableBytes());
    }

    /**
     * Returns an {@link java.io.InputStream} view backed by this buffer's readable bytes.
     * Advancing the stream advances this buffer's {@link #readerIndex()}.
     */
    public java.io.InputStream asInputStream() {
        return new java.io.InputStream() {
            @Override
            public int read() {
                return isReadable() ? (readByte() & 0xFF) : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (!isReadable()) return -1;
                int toRead = Math.min(len, readableBytes());
                readBytes(b, off, toRead);
                return toRead;
            }

            @Override
            public int available() {
                return readableBytes();
            }
        };
    }

    /**
     * Returns an {@link java.io.OutputStream} view backed by this buffer's writer.
     * Writing to the stream writes directly into this buffer.
     */
    public java.io.OutputStream asOutputStream() {
        return new java.io.OutputStream() {
            @Override
            public void write(int b) {
                writeByte((byte) b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                writeBytes(b, off, len);
            }
        };
    }

}