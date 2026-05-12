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

    BufferWriter markWriterIndex();

    BufferWriter resetWriterIndex();

    BufferWriter writeBytes(byte[] data);

    BufferWriter writeBytes(byte[] data, int offset, int length);

    BufferWriter writeString(String data, Charset charset);

    BufferWriter writeString(String data);

    BufferWriter writeEnum(Enum<?> enumVal);

    <T extends Enum<T>, S> BufferWriter writeEnum(T value, Function<T, S> stateMapper, AbstractCallableEncoder<? super S> stateEncoder);

    BufferWriter writeUuid(UUID uuid);

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

    <T> BufferWriter writeOptional(Optional<T> optional, AbstractCallableEncoder<? super T> encoder);

    <T extends AbstractEncoder> BufferWriter writeOptional(Optional<T> optional);

    <T extends AbstractEncoder> BufferWriter writeObject(T object);

    <T> BufferWriter writeIterable(Iterable<T> iterable, int size, AbstractCallableEncoder<? super T> encoder);

    <T> BufferWriter writeCollection(Collection<T> collection, AbstractCallableEncoder<? super T> encoder);

    <T extends AbstractEncoder> BufferWriter writeCollection(Collection<T> collection);

    <K, V> BufferWriter writeMap(Map<K, V> map, AbstractCallableEncoder<Pair<K, V>> encoder);

    <K, V> BufferWriter writeMap(Map<K, V> map, AbstractCallableEncoder<? super K> kEncoder, AbstractCallableEncoder<? super V> vEncoder);

    <K extends Enum<K>, V> BufferWriter writeEnumMap(EnumMap<K, V> map, AbstractCallableEncoder<? super V> vEncoder);

    BufferWriter wrapData(AbstractEncoder encoder);

    BufferWriter writePercentual(double percent, double scale);

    BufferWriter writePosition(int x, int y, int z);

    BufferWriter writeVarPosition(int x, int y, int z);

    BufferWriter writeFixedInt(double value, int fractionBits);

    BufferWriter writeFixedVarInt(double value, int fractionBits);

    BufferWriter writeFixedVarLong(double value, int fractionBits);

    BufferWriter writeFixedPosition(double x, double y, double z, int fractionBits);
}
