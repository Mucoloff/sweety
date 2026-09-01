package dev.sweety.data.buffer;

import dev.sweety.data.buffer.io.AbstractDecoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Read-only buffer API for {@link AbstractDecoder} implementations (no CRTP type parameter on codecs).
 */
public interface BufferReader {

    int readInt();

    int readVarInt();

    long readVarLong();

    int readVarIntZigZag();

    long readVarLongZigZag();

    double readDouble();

    short readShort();

    byte readByte();

    boolean readBoolean();

    char readChar();

    float readFloat();

    long readLong();

    short readUnsignedByte();

    byte getByte(int index);

    short getShort(int index);

    int getInt(int index);

    long getLong(int index);

    float getFloat(int index);

    double getDouble(int index);

    char getChar(int index);

    boolean isReadable();

    boolean isReadable(int bytes);

    int readableBytes();

    /** True when at least one more byte of this packet body is available. See the protocol-evolution
     *  rule: trailing fields added after a packet shipped must be read only when present, so an older
     *  peer that never wrote them leaves them at their defaults instead of failing the whole decode. */
    default boolean hasMore() {
        return readableBytes() > 0;
    }

    int readerIndex();

    /** Write cursor upper bound when this reader is backed by a read/write buffer (e.g. Netty slice). */
    int writerIndex();

    BufferReader readerIndex(int readerIndex);

    BufferReader markReaderIndex();

    BufferReader resetReaderIndex();

    BufferReader discardReadBytes();

    BufferReader readBytes(byte[] data);

    BufferReader readBytes(byte[] data, int offset, int length);

    byte[] readAllBytes();

    byte[] getBytes();

    String readString(Charset charset);

    String readString();

    String readCurrencyCode();

    /** Reads a value written by {@link BufferWriter#writeDynamic}. */
    Object readDynamic();

    <T extends Enum<T>> T readEnum(Class<T> clazz);

    <T extends Enum<T>, S> T readEnum(AbstractCallableDecoder<? extends S> stateDecoder, Function<S, T> mapper);

    UUID readUuid();

    String[] readStringArray();

    byte[] readByteArray();

    boolean[] readBooleanArray();

    char[] readCharArray();

    int[] readIntArray();

    int[] readVarIntArray();

    short[] readShortArray();

    float[] readFloatArray();

    double[] readDoubleArray();

    long[] readVarLongArray();

    <T> T readObject(AbstractCallableDecoder<? extends T> decoder);

    <T> Optional<T> readOptional(AbstractCallableDecoder<? extends T> decoder);

    <T extends AbstractDecoder> Optional<T> readOptional(Supplier<T> factory);

    <T extends AbstractDecoder> T readObject(Supplier<T> factory);

    <T, C extends Collection<T>> C readCollection(AbstractCallableDecoder<? extends T> decoder, IntFunction<C> collectionFactory);

    <T> List<T> readList(AbstractCallableDecoder<? extends T> decoder);

    <T> T[] readArray(AbstractCallableDecoder<? extends T> decoder, IntFunction<T[]> arrayFactory);

    <T extends AbstractDecoder> List<T> readCollection(Supplier<T> factory);

    <K, V> Map<K, V> readMap(AbstractCallableDecoder<Pair<K, V>> decoder, IntFunction<Map<K, V>> mapFactory);

    <K, V> Map<K, V> readMap(AbstractCallableDecoder<K> kDecoder, AbstractCallableDecoder<V> vDecoder, IntFunction<Map<K, V>> mapFactory);

    <K extends Enum<K>, V> EnumMap<K, V> readEnumMap(Class<K> keyClass, AbstractCallableDecoder<V> vDecoder);

    /** Reads an {@link EnumMap} written by the generic {@code writeMap} or {@code writeEnumMap}. Requires the enum {@link Class} to reconstruct the map. */
    <K extends Enum<K>, V> EnumMap<K, V> readMap(Class<K> keyClass, AbstractCallableDecoder<V> vDecoder);

    <E extends Enum<E>> EnumSet<E> readEnumSet(Class<E> type);

    /** Reads an {@link EnumSet} written by {@code writeCollection} or {@code writeEnumSet}. Requires the enum {@link Class} to reconstruct the set. */
    <E extends Enum<E>> EnumSet<E> readCollection(Class<E> type);

    <V> Int2ObjectOpenHashMap<V> readInt2ObjectMap(AbstractCallableDecoder<V> vDecoder);
    <V> Long2ObjectOpenHashMap<V> readLong2ObjectMap(AbstractCallableDecoder<V> vDecoder);
    <K> Object2IntOpenHashMap<K> readObject2IntMap(AbstractCallableDecoder<K> kDecoder);
    <K> Object2LongOpenHashMap<K> readObject2LongMap(AbstractCallableDecoder<K> kDecoder);
    IntOpenHashSet readIntSet();
    IntArrayList readIntList();
    LongOpenHashSet readLongSet();
    LongArrayList readLongList();

    double readPercentual(double scale);

    int[] readPosition();

    int[] readVarPosition();

    double readFixedInt(int fractionBits);

    double readFixedVarInt(int fractionBits);

    double readFixedVarLong(int fractionBits);

    double[] readFixedPosition(int fractionBits);

    /**
     * Reads bytes from this buffer and writes them to an {@link java.io.OutputStream} in chunks.
     * Automatically wraps the stream in a {@link java.io.BufferedOutputStream} if not already buffered.
     *
     * @param out       the destination stream
     * @param length    total bytes to write
     * @param chunkSize chunk transfer buffer size (default: 8192)
     * @return total bytes transferred
     * @throws java.io.IOException on I/O error
     */
    default int readToStream(java.io.OutputStream out, int length, int chunkSize) throws java.io.IOException {
        java.io.OutputStream buffered = (out instanceof java.io.BufferedOutputStream)
                ? out
                : new java.io.BufferedOutputStream(out, Math.max(256, chunkSize));

        byte[] chunk = new byte[Math.min(length, Math.max(256, chunkSize))];
        int remaining = length;
        while (remaining > 0) {
            int toWrite = Math.min(chunk.length, remaining);
            readBytes(chunk, 0, toWrite);
            buffered.write(chunk, 0, toWrite);
            remaining -= toWrite;
        }
        buffered.flush();
        return length;
    }

    default int readToStream(java.io.OutputStream out, int length) throws java.io.IOException {
        return readToStream(out, length, 8192);
    }
}
