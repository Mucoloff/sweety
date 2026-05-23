package dev.sweety.data.buffer;

import dev.sweety.data.HasId;
import dev.sweety.data.buffer.io.AbstractDecoder;
import dev.sweety.data.buffer.io.AbstractEncoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableEncoder;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.math.MathUtils;
import dev.sweety.math.pool.Release;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public abstract class AbstractBuffer<Self extends AbstractBuffer<Self>> implements BufferReader, BufferWriter, PackedBooleanAccessor<Self>, AutoCloseable {
    protected static final int MAX_ARRAY_SIZE = 1 << 23; // 8MB — ForwardData batches exceed 1MB at 3×1500p
    private static final int MAX_STRING_BYTES = 1 << 20;

    public abstract void clear();

    /** Reset state for pool reuse. Called by the allocator after reclaiming from the pool. */
    protected abstract void poolReset();

    public abstract Self discardReadBytes();

    public abstract int capacity();

    public abstract int writableBytes();

    public abstract Self ensureWritable(int minWritableBytes);

    public boolean isReadable(int bytes) {
        return readableBytes() >= bytes;
    }

    //use writeVarInt
    @Deprecated
    public abstract Self writeInt(int value);

    //use readVarInt
    @Deprecated
    public abstract int readInt();

    private void writeVarUnsigned(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        writeByte((byte) value);
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
            if (numRead > maxBytes) throw new PacketDecodeException("VarInt/VarLong too big").runtime();
        } while ((read & 0x80) != 0);

        return result;
    }

    public Self writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        writeByte((byte) value);
        return self();
    }

    public int readVarInt() {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = readByte();
            result |= (read & 0x7F) << (7 * numRead);
            numRead++;
            if (numRead > 5) throw new PacketDecodeException("VarInt too big").runtime();
        } while ((read & 0x80) != 0);
        return result;
    }

    public Self writeVarLong(long value) {
        writeVarUnsigned(value);
        return self();
    }

    public long readVarLong() {
        return readVarUnsigned(10);
    }

    public abstract Self writeDouble(double value);

    public abstract double readDouble();

    public abstract Self writeShort(short value);

    public abstract short readShort();

    public abstract Self writeByte(byte value);

    public abstract byte readByte();

    private byte writeMask = 0, writeMaskIndex = 0;
    private int writePosIndex = 0;
    private byte readMask = 0, readMaskIndex = 0;

    @Override
    public Self writeBoolean(boolean value) {
        return PackedBooleanAccessor.super.writeBoolean(value);
    }

    @Override
    public boolean readBoolean() {
        return PackedBooleanAccessor.super.readBoolean();
    }

    @Override public byte writeMask() { return writeMask; }
    @Override public void writeMask(byte writeMask) { this.writeMask = writeMask; }

    @Override public byte writeMaskIndex() { return writeMaskIndex; }
    @Override public void writeMaskIndex(byte writeMaskIndex) { this.writeMaskIndex = writeMaskIndex; }

    @Override public int writePosIndex() { return writePosIndex; }
    @Override public void writePosIndex(int writePosIndex) { this.writePosIndex = writePosIndex; }

    @Override public byte readMask() { return readMask; }
    @Override public void readMask(byte readMask) { this.readMask = readMask; }

    @Override public byte readMaskIndex() { return readMaskIndex; }
    @Override public void readMaskIndex(byte readMaskIndex) { this.readMaskIndex = readMaskIndex; }

    public abstract Self setByte(int index, byte value);

    public abstract byte getByte(int index);

    public abstract Self setShort(int index, short value);

    public abstract short getShort(int index);

    public abstract Self setInt(int index, int value);

    public abstract int getInt(int index);

    public abstract Self setLong(int index, long value);

    public abstract long getLong(int index);

    public abstract Self setFloat(int index, float value);

    public abstract float getFloat(int index);

    public abstract Self setDouble(int index, double value);

    public abstract double getDouble(int index);

    public abstract Self setChar(int index, char value);

    public abstract char getChar(int index);

    public abstract boolean isReadable();

    public abstract Self writeChar(char value);

    public abstract char readChar();

    public abstract Self writeFloat(float value);

    public abstract float readFloat();

    //use writeVarLong
    @Deprecated
    public abstract Self writeLong(long value);

    //use readVarLong
    @Deprecated
    public abstract long readLong();

    public abstract short readUnsignedByte();

    public Self writeString(String data, Charset charset) {
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

    public Self writeString(String data) {
        return writeString(data, StandardCharsets.UTF_8);
    }

    public String readString() {
        return readString(StandardCharsets.UTF_8);
    }

    public Self writeStringArray(String... array) {
        if (writeNullCheck(array)) return self();
        writeVarInt(array.length);
        for (String i : array) writeString(i);
        return self();
    }

    public String[] readStringArray() {
        if (!readPresence()) return null;
        int len = readBoundedLength("String[]", MAX_ARRAY_SIZE);
        String[] arr = new String[len];
        for (int i = 0; i < len; i++) arr[i] = readString();
        return arr;
    }

    public Self writeEnum(Enum<?> enumVal) {
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
                throw new PacketDecodeException("Invalid enum id: " + val).runtime();
            return result;
        }

        T[] constants = clazz.getEnumConstants();
        if (val >= 0 && val < constants.length) return constants[val];
        throw new PacketDecodeException("Invalid enum ordinal: " + val).runtime();
    }

    public <T extends Enum<T>, S> Self writeEnum(T value, Function<T, S> stateMapper, AbstractCallableEncoder<? super S> stateEncoder) {
        Self self = self();
        stateEncoder.write(self, stateMapper.apply(value));
        return self;
    }

    public <T extends Enum<T>, S> T readEnum(AbstractCallableDecoder<? extends S> stateDecoder, Function<S, T> mapper) {
        return mapper.apply(stateDecoder.read(self()));
    }

    public Self writeUuid(UUID uuid) {
        return writeVarLong(uuid.getMostSignificantBits()).writeVarLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        requireReadable(1, "uuid");
        final long mst = readVarLong();
        requireReadable(1, "uuid");
        return new UUID(mst, readVarLong());
    }

    public Self writeByteArray(byte... bytes) {
        return writeVarInt(bytes.length).writeBytes(bytes);
    }

    public byte[] readByteArray() {
        int len = readBoundedLength("byte[]", MAX_ARRAY_SIZE);
        requireReadable(len, "byte[]");
        byte[] bytes = new byte[len];
        this.readBytes(bytes);
        return bytes;
    }

    public Self writeBooleanArray(boolean... array) {
        writeVarInt(array.length);
        for (boolean i : array) writeBoolean(i);
        return self();
    }

    public boolean[] readBooleanArray() {
        int len = readBoundedLength("boolean[]", MAX_ARRAY_SIZE);
        boolean[] arr = new boolean[len];
        for (int i = 0; i < len; i++) arr[i] = readBoolean();
        return arr;
    }

    public Self writeCharArray(char... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Character.BYTES);
        for (char i : array) writeChar(i);
        return self();
    }

    public char[] readCharArray() {
        int len = readBoundedLength("char[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Character.BYTES, "char[]");
        char[] arr = new char[len];
        for (int i = 0; i < len; i++) arr[i] = readChar();
        return arr;
    }

    public Self writeIntArray(int... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Integer.BYTES);
        for (int i : array) writeInt(i);
        return self();
    }

    public int[] readIntArray() {
        int len = readBoundedLength("int[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Integer.BYTES, "int[]");
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readInt();
        return arr;
    }

    public Self writeVarIntArray(int... array) {
        writeVarInt(array.length);
        for (int i : array) writeVarInt(i);
        return self();
    }

    public int[] readVarIntArray() {
        int len = readBoundedLength("varInt[]", MAX_ARRAY_SIZE);
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readVarInt();
        return arr;
    }

    public Self writeShortArray(short... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Short.BYTES);
        for (short i : array) writeShort(i);
        return self();
    }

    public short[] readShortArray() {
        int len = readBoundedLength("short[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Short.BYTES, "short[]");
        short[] arr = new short[len];
        for (int i = 0; i < len; i++) arr[i] = readShort();
        return arr;
    }

    public Self writeFloatArray(float... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Float.BYTES);
        for (float i : array) writeFloat(i);
        return self();
    }

    public float[] readFloatArray() {
        int len = readBoundedLength("float[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Float.BYTES, "float[]");
        float[] arr = new float[len];
        for (int i = 0; i < len; i++) arr[i] = readFloat();
        return arr;
    }

    public Self writeDoubleArray(double... array) {
        writeVarInt(array.length);
        ensureWritable(array.length * Double.BYTES);
        for (double i : array) writeDouble(i);
        return self();
    }

    public double[] readDoubleArray() {
        int len = readBoundedLength("double[]", MAX_ARRAY_SIZE);
        requireReadable((long) len * Double.BYTES, "double[]");
        double[] arr = new double[len];
        for (int i = 0; i < len; i++) arr[i] = readDouble();
        return arr;
    }

    public Self writeVarLongArray(long... array) {
        writeVarInt(array.length);
        for (long i : array) writeVarLong(i);
        return self();
    }

    public long[] readVarLongArray() {
        int len = readBoundedLength("varLong[]", MAX_ARRAY_SIZE);
        long[] arr = new long[len];
        for (int i = 0; i < len; i++) arr[i] = readVarLong();
        return arr;
    }

    private Self writePresence(boolean present) {
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
     * Writes a single presence marker then, if present, the payload. Used by {@link #writeObject}
     * and {@link #writeOptional} so both share the same wire layout without chaining public overloads.
     */
    private <T> Self writeMarkedPayload(boolean present, @Nullable T valueIfPresent, AbstractCallableEncoder<? super T> encoder) {
        writePresence(present);
        Self self = self();
        if (!present) return self;
        encoder.write(self, valueIfPresent);
        return self;
    }

    /**
     * Reads a presence marker; if absent returns {@code null}, otherwise decodes the payload.
     */
    private <T> @Nullable T readMarkedPayload(AbstractCallableDecoder<? extends T> decoder) {
        if (!readPresence()) return null;
        return decoder.read(self());
    }

    public <T> Self writeObject(@Nullable T object, AbstractCallableEncoder<? super T> encoder) {
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

    public <T> Self writeOptional(@Nullable T value, AbstractCallableEncoder<? super T> encoder) {
        return writeMarkedPayload(value != null, value, encoder);
    }

    public <T extends AbstractEncoder> Self writeOptional(@Nullable T value) {
        return writeOptional(value, (buffer, t) -> t.write(buffer));
    }

    public <T extends AbstractEncoder> Self writeObject(T object) {
        return writeObject(object, (buffer, t) -> t.write(buffer));
    }

    public <T extends AbstractDecoder> T readObject(Supplier<T> factory) {
        return readObject(buffer -> {
            T obj = factory.get();
            obj.read(buffer);
            return obj;
        });
    }

    public <T> Self writeIterable(Iterable<T> iterable, int size, AbstractCallableEncoder<? super T> encoder) {
        if (writeNullCheck(iterable)) return self();
        writeVarInt(size);
        Self self = self();
        for (T entry : iterable) encoder.write(self, entry);
        return self;
    }

    @SafeVarargs
    public final <T> Self writeArray(AbstractCallableEncoder<? super T> encoder, T... array) {
        if (writeNullCheck(array)) return self();
        writeVarInt(array.length);
        Self self = self();
        for (T entry : array) encoder.write(self, entry);
        return self;
    }

    public <T> Self writeCollection(Collection<T> collection, AbstractCallableEncoder<? super T> encoder) {
        return writeIterable(collection, collection.size(), encoder);
    }

    public <T extends AbstractEncoder> Self writeCollection(Collection<T> collection) {
        return writeCollection(collection, (buffer, t) -> t.write(buffer));
    }

    public <T, C extends Collection<T>> C readCollection(AbstractCallableDecoder<? extends T> decoder, IntFunction<C> collectionFactory) {
        if (!readPresence()) return null;
        final int size = readBoundedLength("collection", MAX_ARRAY_SIZE);
        final C collection = collectionFactory.apply(size);
        Self self = self();
        for (int i = 0; i < size; i++) collection.add(decoder.read(self));
        return collection;
    }

    public <T> List<T> readList(AbstractCallableDecoder<? extends T> decoder) {
        return readCollection(decoder, ArrayList::new);
    }

    public <T> T[] readArray(AbstractCallableDecoder<? extends T> decoder, IntFunction<T[]> arrayFactory) {
        if (!readPresence()) return null;
        int size = readBoundedLength("array", MAX_ARRAY_SIZE);
        T[] array = arrayFactory.apply(size);
        Self self = self();
        for (int i = 0; i < size; i++) array[i] = decoder.read(self);
        return array;
    }

    public <T extends AbstractDecoder> List<T> readCollection(Supplier<T> factory) {
        return readList(buffer -> buffer.readObject(factory));
    }

    /** @deprecated Use {@link #writeMap(Map, AbstractCallableEncoder, AbstractCallableEncoder)} to avoid per-entry Pair allocation. */
    @Deprecated
    public <K, V> Self writeMap(Map<K, V> map, AbstractCallableEncoder<Pair<K, V>> encoder) {
        if (writeNullCheck(map)) return self();
        writeVarInt(map.size());
        Self self = self();
        for (Map.Entry<K, V> entry : map.entrySet()) encoder.write(self, Pair.of(entry.getKey(), entry.getValue()));
        return self;
    }

    /** @deprecated Use {@link #readMap(AbstractCallableDecoder, AbstractCallableDecoder, IntFunction)} to avoid per-entry Pair allocation. */
    @Deprecated
    public <K, V> Map<K, V> readMap(AbstractCallableDecoder<Pair<K, V>> decoder, IntFunction<Map<K, V>> mapFactory) {
        if (!readPresence()) return null;
        int size = readBoundedLength("map", MAX_ARRAY_SIZE);
        Map<K, V> map = mapFactory.apply(size);
        Self self = self();
        for (int i = 0; i < size; i++) {
            Pair<K, V> pair = decoder.read(self);
            map.put(pair.key(), pair.value());
        }
        return map;
    }

    public <K, V> Self writeMap(Map<K, V> map, AbstractCallableEncoder<? super K> kEncoder, AbstractCallableEncoder<? super V> vEncoder) {
        if (writeNullCheck(map)) return self();
        writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            kEncoder.write(self(), entry.getKey());
            vEncoder.write(self(), entry.getValue());
        }
        return self();
    }

    public <K, V> Map<K, V> readMap(AbstractCallableDecoder<K> kDecoder, AbstractCallableDecoder<V> vDecoder, IntFunction<Map<K, V>> mapFactory) {
        if (!readPresence()) return null;
        int size = readBoundedLength("map", MAX_ARRAY_SIZE);
        Map<K, V> map = mapFactory.apply(size);
        for (int i = 0; i < size; i++) {
            K key = kDecoder.read(self());
            V val = vDecoder.read(self());
            map.put(key, val);
        }
        return map;
    }

    public <K extends Enum<K>, V> Self writeEnumMap(EnumMap<K, V> map, AbstractCallableEncoder<? super V> vEncoder) {
        return writeMap(map, BufferWriter::writeEnum, vEncoder);
    }

    public <K extends Enum<K>, V> EnumMap<K, V> readEnumMap(Class<K> keyClass, AbstractCallableDecoder<V> vDecoder) {
        if (!readPresence()) return null;
        int size = readBoundedLength("enumMap", MAX_ARRAY_SIZE);
        EnumMap<K, V> map = new EnumMap<>(keyClass);
        for (int i = 0; i < size; i++) {
            K key = readEnum(keyClass);
            V val = vDecoder.read(self());
            map.put(key, val);
        }
        return map;
    }

    public <E extends Enum<E>> Self writeEnumSet(EnumSet<E> set, Class<E> type) {
        if (writeNullCheck(set)) return self();
        E[] universe = type.getEnumConstants();
        if (universe.length <= 64) {
            long bits = 0;
            for (E e : set) bits |= 1L << e.ordinal();
            return writeVarLong(bits);
        }
        writeVarInt(set.size());
        for (E e : set) writeEnum(e);
        return self();
    }

    public <E extends Enum<E>> EnumSet<E> readEnumSet(Class<E> type) {
        if (!readPresence()) return null;
        E[] universe = type.getEnumConstants();
        EnumSet<E> set = EnumSet.noneOf(type);
        if (universe.length <= 64) {
            long bits = readVarLong();
            for (E e : universe) {
                if ((bits & (1L << e.ordinal())) != 0) set.add(e);
            }
            return set;
        }
        int size = readBoundedLength("enumSet", MAX_ARRAY_SIZE);
        for (int i = 0; i < size; i++) {
            set.add(readEnum(type));
        }
        return set;
    }

    public abstract boolean release();

    /** Delegates to {@link #release()} for try-with-resources support. */
    @Release
    @Override
    public void close() {
        release();
    }

    public abstract Self retain(int increment);

    public abstract Self retain();

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

    public abstract Self resetReaderIndex();

    public abstract Self markReaderIndex();

    public abstract int readerIndex();

    public abstract Self readerIndex(int readerIndex);

    public abstract Self resetWriterIndex();

    public abstract Self markWriterIndex();

    public abstract int writerIndex();

    public abstract Self writerIndex(int writerIndex);

    public abstract Self readBytes(byte[] data);

    public abstract Self writeBytes(byte[] data);

    public abstract Self writeBytes(byte[] data, int offset, int length);

    public Self wrapData(AbstractEncoder encoder) {
        byte[] bytes = readAllBytes();
        encoder.write(self());
        return writeBytes(bytes);
    }

    // Prefer zero-copy when possible
    public abstract Self writeBuffer(Self other);

    public abstract Self readSlice(int length);

    // Return a retained slice to safely pass across components without immediate copy
    public abstract Self readRetainedSlice(int length);

    public abstract Self slice();

    // Create a slice view from current readerIndex with specified length
    public abstract Self slice(int index, int length);

    // Retained slice for longer-lived sharing
    public abstract Self retainedSlice(int index, int length);

    public Self writePercentual(double percent, double scale) {
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

    public static int posX(long packed) { return (int) (packed >> 38); }
    public static int posY(long packed) { return (int) (packed << 52 >> 52); }
    public static int posZ(long packed) { return (int) (packed << 26 >> 38); }

    public Self writePosition(int x, int y, int z) {
        return writeLong(packPosition(x, y, z));
    }

    public long readPackedPosition() {
        return readLong();
    }

    /** @deprecated Use {@link #readPackedPosition()} + {@link #posX}/{@link #posY}/{@link #posZ} to avoid int[] allocation. */
    @Deprecated
    public int[] readPosition() {
        long val = readLong();
        return new int[]{posX(val), posY(val), posZ(val)};
    }

    public Self writeVarPosition(int x, int y, int z) {
        return writeVarLong(packPosition(x, y, z));
    }

    public long readPackedVarPosition() {
        return readVarLong();
    }

    /** @deprecated Use {@link #readPackedVarPosition()} + {@link #posX}/{@link #posY}/{@link #posZ} to avoid int[] allocation. */
    @Deprecated
    public int[] readVarPosition() {
        long val = readVarLong();
        return new int[]{posX(val), posY(val), posZ(val)};
    }

    public Self writeFixedInt(double value, int fractionBits) {
        int fixed = (int) Math.round(value * (1 << fractionBits));
        return writeInt(fixed);
    }

    public double readFixedInt(int fractionBits) {
        return readInt() / (double) (1 << fractionBits);
    }

    public Self writeFixedVarInt(double value, int fractionBits) {
        int fixed = (int) Math.round(value * (1 << fractionBits));
        return writeVarInt(fixed);
    }

    public double readFixedVarInt(int fractionBits) {
        return readVarInt() / (double) (1 << fractionBits);
    }

    public Self writeFixedVarLong(double value, int fractionBits) {
        long fixed = Math.round(value * (1L << fractionBits));
        return writeVarLong(fixed);
    }

    public double readFixedVarLong(int fractionBits) {
        return readVarLong() / (double) (1L << fractionBits);
    }

    public Self writeFixedPosition(double x, double y, double z, int fractionBits) {
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
            throw new PacketDecodeException(label + " length out of bounds: " + len).runtime();
        }
        return len;
    }

    protected void requireReadable(long bytes, String label) {
        if (bytes < 0 || bytes > Integer.MAX_VALUE) {
            throw new PacketDecodeException("Invalid byte length for " + label + ": " + bytes).runtime();
        }
        if (this.readableBytes() < (int) bytes) {
            throw new PacketDecodeException("Not enough bytes for " + label + ": requested=" + bytes
                    + ", available=" + this.readableBytes()).runtime();
        }
    }

    /**
     * CRTP helper: unchecked cast matches {@link Self}-bound subclasses only.
     */
    private Self self() {
        //noinspection unchecked
        return (Self) this;
    }

    @Override
    public String toString() {
        return "%s(ridx=%d, widx=%d, readable=%d)".formatted(getClass().getSimpleName(), readerIndex(), writerIndex(), readableBytes());
    }

}