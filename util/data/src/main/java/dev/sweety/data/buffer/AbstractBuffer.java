package dev.sweety.data.buffer;

import dev.sweety.data.HasId;
import dev.sweety.data.buffer.io.AbstractDecoder;
import dev.sweety.data.buffer.io.AbstractEncoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableEncoder;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.math.MathUtils;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public abstract class AbstractBuffer<Self extends AbstractBuffer<Self>> {
    private static final int MAX_ARRAY_SIZE = 1 << 23; // 8MB — ForwardData batches exceed 1MB at 3×1500p
    private static final int MAX_STRING_BYTES = 1 << 20;

    public abstract void clear();

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
        writeVarUnsigned(value & 0xFFFFFFFFL);
        return self();
    }

    public int readVarInt() {
        return (int) readVarUnsigned(5);
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

    public Self writeBoolean(boolean value) {
        if (writeMaskIndex % 8 == 0) {
            writePosIndex = writerIndex();
            writeByte(writeMask = 0);
        }

        if (value) writeMask |= (byte) (1 << (writeMaskIndex % 8));

        setByte(writePosIndex, writeMask);
        writeMaskIndex++;
        return self();
    }

    public boolean readBoolean() {
        if (readMaskIndex % 8 == 0) {
            if (!isReadable())
                throw new PacketDecodeException("Unable to read boolean", new EOFException()).runtime();
            readMask = readByte();
        }

        return ((readMask >> (readMaskIndex++ % 8)) & 1) != 0;
    }

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

    public Self writeEnum(Enum<?> enumVal) {
        final int val = enumVal instanceof HasId hasId ? hasId.id() : enumVal.ordinal();
        return writeVarInt(val);
    }

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        int val = this.readVarInt();

        if (HasId.class.isAssignableFrom(clazz)) {
            for (T c : constants) {
                if (((HasId) c).id() != val) continue;
                return c;
            }
            throw new PacketDecodeException("Invalid enum id: " + val).runtime();
        }

        if (val >= 0 && val < constants.length) return constants[val];
        else throw new PacketDecodeException("Invalid enum ordinal: " + val).runtime();
    }

    public <T extends Enum<T>, S> Self writeEnum(T value, Function<T, S> stateMapper, AbstractCallableEncoder<? super S, Self> stateEncoder) {
        Self self = self();
        stateEncoder.write(self, stateMapper.apply(value));
        return self;
    }

    public <T extends Enum<T>, S> T readEnum(AbstractCallableDecoder<? extends S, Self> stateDecoder, Function<S, T> mapper) {
        return mapper.apply(stateDecoder.read(self()));
    }

    public Self writeUuid(UUID uuid) {
        return writeVarLong(uuid.getMostSignificantBits()).writeVarLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        if (this.readableBytes() < 1)
            throw new PacketDecodeException("Not enough readableBytes to read UUID: " + readableBytes()).runtime();
        final long mst = readVarLong();
        if (this.readableBytes() < 1)
            throw new PacketDecodeException("Not enough readableBytes to read UUID: " + readableBytes()).runtime();
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
        requireReadable((len + 7) / 8, "boolean[]");
        boolean[] arr = new boolean[len];
        for (int i = 0; i < len; i++) arr[i] = readBoolean();
        return arr;
    }

    public Self writeCharArray(char... array) {
        writeVarInt(array.length);
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
        try {
            return this.readBoolean();
        } catch (Exception e) {
            PacketDecodeException ex = new PacketDecodeException("Unable to read presence marker", new EOFException());
            ex.addStackTrace(e.getStackTrace());
            throw ex.runtime();
        }
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
    private <T> Self writeMarkedPayload(boolean present, @Nullable T valueIfPresent, AbstractCallableEncoder<? super T, Self> encoder) {
        writePresence(present);
        if (!present) return self();
        encoder.write(self(), valueIfPresent);
        return self();
    }

    /**
     * Reads a presence marker; if absent returns {@code null}, otherwise decodes the payload.
     */
    private <T> @Nullable T readMarkedPayload(AbstractCallableDecoder<? extends T, Self> decoder) {
        if (!readPresence()) return null;
        return decoder.read(self());
    }

    public <T> Self writeObject(@Nullable T object, AbstractCallableEncoder<? super T, Self> encoder) {
        return writeMarkedPayload(object != null, object, encoder);
    }

    public <T> T readObject(AbstractCallableDecoder<? extends T, Self> decoder) {
        return readMarkedPayload(decoder);
    }

    public <T> Optional<T> readOptional(AbstractCallableDecoder<? extends T, Self> decoder) {
        return Optional.ofNullable(readMarkedPayload(decoder));
    }

    public <T extends AbstractDecoder<Self>> Optional<T> readOptional(Supplier<T> factory) {
        return Optional.ofNullable(readObject(factory));
    }

    public <T> Self writeOptional(final Optional<T> optional, AbstractCallableEncoder<? super T, Self> encoder) {
        return writeMarkedPayload(optional.isPresent(), optional.orElse(null), encoder);
    }

    public <T extends AbstractEncoder<Self>> Self writeOptional(final Optional<T> optional) {
        return writeOptional(optional, (buffer, t) -> t.write(buffer));
    }

    public <T extends AbstractEncoder<Self>> Self writeObject(T object) {
        return writeObject(object, (buffer, t) -> t.write(buffer));
    }

    public <T extends AbstractDecoder<Self>> T readObject(Supplier<T> factory) {
        return readObject(buffer -> {
            T obj = factory.get();
            obj.read(buffer);
            return obj;
        });
    }

    public <T> Self writeIterable(Iterable<T> iterable, int size, AbstractCallableEncoder<? super T, Self> encoder) {
        if (writeNullCheck(iterable)) return self();
        writeVarInt(size);
        for (T entry : iterable) {
            writeObject(entry, encoder);
        }
        return self();
    }

    @SafeVarargs
    public final <T> Self writeArray(AbstractCallableEncoder<? super T, Self> encoder, T... array) {
        if (writeNullCheck(array)) return self();
        writeVarInt(array.length);
        for (T entry : array) writeObject(entry, encoder);
        return self();
    }

    public <T> Self writeCollection(Collection<T> collection, AbstractCallableEncoder<? super T, Self> encoder) {
        return writeIterable(collection, collection.size(), encoder);
    }

    public <T extends AbstractEncoder<Self>> Self writeCollection(Collection<T> collection) {
        return writeCollection(collection, (buffer, t) -> t.write(buffer));
    }

    public <T, C extends Collection<T>> C readCollection(AbstractCallableDecoder<? extends T, Self> decoder, IntFunction<C> collectionFactory) {
        if (!readPresence()) return null;
        final int size = readBoundedLength("collection", MAX_ARRAY_SIZE);
        final C collection = collectionFactory.apply(size);
        for (int i = 0; i < size; i++) collection.add(readObject(decoder));
        return collection;
    }

    public <T> List<T> readList(AbstractCallableDecoder<? extends T, Self> decoder) {
        return readCollection(decoder, ArrayList::new);
    }

    public <T> T[] readArray(AbstractCallableDecoder<? extends T, Self> decoder, IntFunction<T[]> arrayFactory) {
        if (!readPresence()) return null;
        int size = readBoundedLength("array", MAX_ARRAY_SIZE);
        T[] array = arrayFactory.apply(size);
        for (int i = 0; i < size; i++) {
            array[i] = readObject(decoder);
        }
        return array;
    }

    public <T extends AbstractDecoder<Self>> List<T> readCollection(Supplier<T> factory) {
        return readList(buffer -> buffer.readObject(factory));
    }

    public <K, V> Self writeMap(Map<K, V> map, AbstractCallableEncoder<Pair<K, V>, Self> encoder) {
        if (writeNullCheck(map)) return self();
        writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            writeObject(Pair.of(entry.getKey(), entry.getValue()), encoder);
        }
        return self();
    }

    public <K, V> Map<K, V> readMap(AbstractCallableDecoder<Pair<K, V>, Self> decoder, IntFunction<Map<K, V>> mapFactory) {
        if (!readPresence()) return null;

        int size = readBoundedLength("map", MAX_ARRAY_SIZE);
        Map<K, V> map = mapFactory.apply(size);
        for (int i = 0; i < size; i++) {
            Pair<K, V> pair = readObject(decoder);
            map.put(pair.key(), pair.value());
        }
        return map;
    }

    public <K, V> Self writeMap(Map<K, V> map, AbstractCallableEncoder<? super K, Self> kEncoder, AbstractCallableEncoder<? super V, Self> vEncoder) {
        return writeMap(map, (buffer, data) -> {
            kEncoder.write(buffer, data.key());
            vEncoder.write(buffer, data.value());
        });
    }

    public <K, V> Map<K, V> readMap(AbstractCallableDecoder<K, Self> kDecoder, AbstractCallableDecoder<V, Self> vDecoder, IntFunction<Map<K, V>> mapFactory) {
        return readMap(buffer -> Pair.of(kDecoder.read(buffer), vDecoder.read(buffer)), mapFactory);
    }

    public <K extends Enum<K>, V> Self writeEnumMap(EnumMap<K, V> map, AbstractCallableEncoder<? super V, Self> vEncoder) {
        return writeMap(map, AbstractBuffer::writeEnum, vEncoder);
    }

    public <K extends Enum<K>, V> EnumMap<K, V> readEnumMap(Class<K> keyClass, AbstractCallableDecoder<V, Self> vDecoder) {
        final Map<K, V> tmp = readMap(buffer -> buffer.readEnum(keyClass), vDecoder, HashMap::new);
        final EnumMap<K, V> map = new EnumMap<>(keyClass);
        if (!tmp.isEmpty()) map.putAll(tmp);
        return map;
    }

    public abstract boolean release();

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

    public Self wrapData(AbstractEncoder<Self> encoder) {
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

    private long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) |
                ((long) (z & 0x3FFFFFF) << 12) |
                ((long) (y & 0xFFF));
    }

    private int[] unpackPosition(long packed) {
        final int x = (int) (packed >> 38);
        final int y = (int) (packed << 52 >> 52);
        final int z = (int) (packed << 26 >> 38);

        return new int[]{x, y, z};
    }

    public Self writePosition(int x, int y, int z) {
        return writeLong(packPosition(x, y, z));
    }

    public int[] readPosition() {
        final long val = readLong();
        return unpackPosition(val);
    }

    public Self writeVarPosition(int x, int y, int z) {
        return writeVarLong(packPosition(x, y, z));
    }

    public int[] readVarPosition() {
        final long val = readVarLong();
        return unpackPosition(val);
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

    private int readBoundedLength(String label, int max) {
        final int len = readVarInt();
        if (len < 0 || len > max) {
            throw new PacketDecodeException(label + " length out of bounds: " + len).runtime();
        }
        return len;
    }

    private void requireReadable(long bytes, String label) {
        if (bytes < 0 || bytes > Integer.MAX_VALUE) {
            throw new PacketDecodeException("Invalid byte length for " + label + ": " + bytes).runtime();
        }
        if (this.readableBytes() < (int) bytes) {
            throw new PacketDecodeException("Not enough bytes for " + label + ": requested=" + bytes
                    + ", available=" + this.readableBytes()).runtime();
        }
    }

    private Self self() {
        //noinspection unchecked
        return (Self) this;
    }

}