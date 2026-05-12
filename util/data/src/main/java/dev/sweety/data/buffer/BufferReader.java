package dev.sweety.data.buffer;

import dev.sweety.data.buffer.io.AbstractDecoder;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;
import it.unimi.dsi.fastutil.Pair;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.EnumMap;
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

    int readableBytes();

    int readerIndex();

    /** Write cursor upper bound when this reader is backed by a read/write buffer (e.g. Netty slice). */
    int writerIndex();

    BufferReader readerIndex(int readerIndex);

    BufferReader markReaderIndex();

    BufferReader resetReaderIndex();

    BufferReader readBytes(byte[] data);

    byte[] readAllBytes();

    byte[] getBytes();

    String readString(Charset charset);

    String readString();

    <T extends Enum<T>> T readEnum(Class<T> clazz);

    <T extends Enum<T>, S> T readEnum(AbstractCallableDecoder<? extends S> stateDecoder, Function<S, T> mapper);

    UUID readUuid();

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

    double readPercentual(double scale);

    int[] readPosition();

    int[] readVarPosition();

    double readFixedInt(int fractionBits);

    double readFixedVarInt(int fractionBits);

    double readFixedVarLong(int fractionBits);

    double[] readFixedPosition(int fractionBits);

}
