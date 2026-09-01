package dev.sweety.data.buffer;

import dev.sweety.data.buffer.io.AbstractEncoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableEncoder;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Write buffer API for {@link AbstractEncoder} implementations (no CRTP type parameter on codecs).
 */
public interface BufferWriter {

    void clear();

    BufferWriter writeInt(int value);

    BufferWriter writeVarInt(int value);

    BufferWriter writeVarLong(long value);

    /** Zig-zag encoded signed varint — cheap for small negative values too, unlike {@link #writeVarInt}. */
    BufferWriter writeVarIntZigZag(int value);

    /** Zig-zag encoded signed varlong — cheap for small negative values too, unlike {@link #writeVarLong}. */
    BufferWriter writeVarLongZigZag(long value);

    BufferWriter writeDouble(double value);

    BufferWriter writeShort(short value);

    BufferWriter writeByte(byte value);

    BufferWriter writeBoolean(boolean value);

    BufferWriter writeChar(char value);

    BufferWriter writeFloat(float value);

    BufferWriter writeLong(long value);

    BufferWriter setByte(int index, byte value);

    BufferWriter setShort(int index, short value);

    BufferWriter setInt(int index, int value);

    BufferWriter setLong(int index, long value);

    BufferWriter setFloat(int index, float value);

    BufferWriter setDouble(int index, double value);

    BufferWriter setChar(int index, char value);

    int writerIndex();

    BufferWriter writerIndex(int writerIndex);

    int capacity();

    int writableBytes();

    BufferWriter ensureWritable(int minWritableBytes);

    BufferWriter markWriterIndex();

    BufferWriter resetWriterIndex();

    BufferWriter writeBytes(byte[] data);

    BufferWriter writeBytes(byte[] data, int offset, int length);

    BufferWriter writeString(String data, Charset charset);

    BufferWriter writeString(String data);

    BufferWriter writeCurrencyCode(String code);

    /** Writes any dynamically-typed scalar/Map/List value — see {@code AbstractBuffer} for the tag set. */
    BufferWriter writeDynamic(Object value);

    BufferWriter writeEnum(Enum<?> enumVal);

    <T extends Enum<T>, S> BufferWriter writeEnum(T value, Function<T, S> stateMapper, AbstractCallableEncoder<? super S> stateEncoder);

    BufferWriter writeUuid(UUID uuid);

    BufferWriter writeStringArray(String... array);

    BufferWriter writeByteArray(byte... bytes);

    BufferWriter writeBooleanArray(boolean... array);

    BufferWriter writeCharArray(char... array);

    BufferWriter writeIntArray(int... array);

    BufferWriter writeVarIntArray(int... array);

    BufferWriter writeShortArray(short... array);

    BufferWriter writeFloatArray(float... array);

    BufferWriter writeDoubleArray(double... array);

    BufferWriter writeVarLongArray(long... array);

    <T> BufferWriter writeObject(@Nullable T object, AbstractCallableEncoder<? super T> encoder);

    <T> BufferWriter writeOptional(@Nullable T value, AbstractCallableEncoder<? super T> encoder);

    @Deprecated
    default <T> BufferWriter writeOptional(Optional<T> optional, AbstractCallableEncoder<? super T> encoder) {
        return writeOptional(optional.orElse(null), encoder);
    }

    <T extends AbstractEncoder> BufferWriter writeOptional(@Nullable T value);

    @Deprecated
    default <T extends AbstractEncoder> BufferWriter writeOptional(Optional<T> optional) {
        return writeOptional(optional.orElse(null));
    }

    <T extends AbstractEncoder> BufferWriter writeObject(T object);

    <T> BufferWriter writeIterable(Iterable<T> iterable, int size, AbstractCallableEncoder<? super T> encoder);

    <T> BufferWriter writeCollection(Collection<T> collection, AbstractCallableEncoder<? super T> encoder);

    <T extends AbstractEncoder> BufferWriter writeCollection(Collection<T> collection);

    <K, V> BufferWriter writeMap(Map<K, V> map, AbstractCallableEncoder<Pair<K, V>> encoder);

    <K, V> BufferWriter writeMap(Map<K, V> map, AbstractCallableEncoder<? super K> kEncoder, AbstractCallableEncoder<? super V> vEncoder);

    <K extends AbstractEncoder, V extends AbstractEncoder> BufferWriter writeMap(Map<K, V> map);

    <K extends Enum<K>, V> BufferWriter writeEnumMap(EnumMap<K, V> map, AbstractCallableEncoder<? super V> vEncoder);

    <E extends Enum<E>> BufferWriter writeEnumSet(java.util.EnumSet<E> set, Class<E> type);

    BufferWriter wrapData(AbstractEncoder encoder);

    BufferWriter writePercentual(double percent, double scale);

    BufferWriter writePosition(int x, int y, int z);

    BufferWriter writeVarPosition(int x, int y, int z);

    BufferWriter writeFixedInt(double value, int fractionBits);

    BufferWriter writeFixedVarInt(double value, int fractionBits);

    BufferWriter writeFixedVarLong(double value, int fractionBits);

    BufferWriter writeFixedPosition(double x, double y, double z, int fractionBits);

    /**
     * Reads from an {@link java.io.InputStream} in chunks and writes all bytes into this buffer.
     * Automatically wraps the stream in a {@link java.io.BufferedInputStream} if not already buffered.
     *
     * @param in        the source stream
     * @param length    total bytes to read
     * @param chunkSize chunk transfer buffer size (default: 8192)
     * @return total bytes written
     * @throws java.io.IOException on I/O error or unexpected EOF
     */
    default int writeFromStream(java.io.InputStream in, int length, int chunkSize) throws java.io.IOException {
        java.io.InputStream buffered = (in instanceof java.io.BufferedInputStream)
                ? in
                : new java.io.BufferedInputStream(in, Math.max(256, chunkSize));

        byte[] chunk = new byte[Math.min(length, Math.max(256, chunkSize))];
        int remaining = length;
        while (remaining > 0) {
            int toRead = Math.min(chunk.length, remaining);
            int read = buffered.read(chunk, 0, toRead);
            if (read < 0) {
                throw new java.io.EOFException("Unexpected end of stream while reading buffered chunk, remaining: " + remaining);
            }
            writeBytes(chunk, 0, read);
            remaining -= read;
        }
        return length;
    }

    default int writeFromStream(java.io.InputStream in, int length) throws java.io.IOException {
        return writeFromStream(in, length, 8192);
    }
}
