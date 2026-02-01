package dev.sweety.netty.packet.buffer;

import dev.sweety.core.math.MathUtils;
import dev.sweety.netty.messaging.exception.PacketDecodeException;
import dev.sweety.netty.packet.buffer.io.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class PacketBuffer {

    private final ByteBuf nettyBuffer;

    public PacketBuffer(ByteBuf nettyBuffer) {
        this.nettyBuffer = nettyBuffer;
    }

    public PacketBuffer() {
        this(Unpooled.buffer());
    }

    public PacketBuffer(byte[] bytes) {
        this(Unpooled.wrappedBuffer(bytes));
    }

    public void clear() {
        this.nettyBuffer.clear();
    }

    public PacketBuffer writeInt(int value) {
        this.nettyBuffer.writeInt(value);
        return this;
    }

    public int readInt() {
        return this.nettyBuffer.readInt();
    }

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

    public PacketBuffer writeVarInt(int value) {
        writeVarUnsigned(value & 0xFFFFFFFFL);
        return this;
    }

    public int readVarInt() {
        return (int) readVarUnsigned(5);
    }

    public PacketBuffer writeVarLong(long value) {
        writeVarUnsigned(value);
        return this;
    }

    public long readVarLong() {
        return readVarUnsigned(10);
    }

    public PacketBuffer writeDouble(double value) {
        this.nettyBuffer.writeDouble(value);
        return this;
    }

    public double readDouble() {
        return this.nettyBuffer.readDouble();
    }

    public PacketBuffer writeShort(short value) {
        this.nettyBuffer.writeShort(value);
        return this;
    }

    public short readShort() {
        return this.nettyBuffer.readShort();
    }

    public PacketBuffer writeByte(byte value) {
        this.nettyBuffer.writeByte(value);
        return this;
    }

    public byte readByte() {
        return this.nettyBuffer.readByte();
    }

    private byte _mask = 0, _maskIndex = 0;
    private int _posIndex = 0;

    public PacketBuffer writeBoolean(boolean value) {
        if (_maskIndex % 8 == 0) {
            _posIndex = nettyBuffer.writerIndex();
            nettyBuffer.writeByte(_mask = 0);
        }

        if (value) _mask |= (byte) (1 << (_maskIndex % 8));

        nettyBuffer.setByte(_posIndex, _mask);
        _maskIndex++;
        return this;
    }

    public boolean readBoolean() {
        if (_maskIndex % 8 == 0) {
            if (!nettyBuffer.isReadable())
                throw new PacketDecodeException("Unable to read boolean", new EOFException()).runtime();
            _mask = nettyBuffer.readByte();
        }

        return ((_mask >> (_maskIndex++ % 8)) & 1) != 0;
    }

    public PacketBuffer writeChar(char value) {
        this.nettyBuffer.writeChar(value);
        return this;
    }

    public char readChar() {
        return this.nettyBuffer.readChar();
    }

    public PacketBuffer writeFloat(float value) {
        this.nettyBuffer.writeFloat(value);
        return this;
    }

    public float readFloat() {
        return this.nettyBuffer.readFloat();
    }

    public PacketBuffer writeLong(long value) {
        this.nettyBuffer.writeLong(value);
        return this;
    }

    public long readLong() {
        return this.nettyBuffer.readLong();
    }

    public short readUnsignedByte() {
        return this.nettyBuffer.readUnsignedByte();
    }

    public PacketBuffer writeString(String data, Charset charset) {
        if (data == null) return writeVarInt(0);
        byte[] bytes = data.getBytes(charset);
        writeVarInt(bytes.length);
        return writeBytes(bytes);
    }

    public String readString(Charset charset) {
        int length = readVarInt();

        if (length < 0) throw new PacketDecodeException("Invalid string length: " + length).runtime();
        if (nettyBuffer.readableBytes() < length)
            throw new IndexOutOfBoundsException("Not enough bytes to read string: requested " + length + ", available " + nettyBuffer.readableBytes());

        byte[] bytes = new byte[length];
        nettyBuffer.readBytes(bytes);
        return new String(bytes, charset);
    }

    public PacketBuffer writeString(String data) {
        return writeString(data, StandardCharsets.UTF_8);
    }

    public String readString() {
        return readString(StandardCharsets.UTF_8);
    }

    public PacketBuffer writeEnum(Enum<?> enumVal) {
        final int val = enumVal instanceof HasId hasId ? hasId.id() : enumVal.ordinal();
        return writeVarInt(val);
    }

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        int val = this.readVarInt();

        if (HasId.class.isAssignableFrom(clazz)){
            for (T c : constants) {
                if (((HasId) c).id() != val) continue;
                return c;
            }
            throw new PacketDecodeException("Invalid enum id: " + val).runtime();
        }

        if (val >= 0 && val < constants.length) return constants[val];
        else throw new PacketDecodeException("Invalid enum ordinal: " + val).runtime();
    }

    public <T extends Enum<T>, S> PacketBuffer writeEnum(T value, Function<T, S> stateMapper, CallableEncoder<? super S> stateEncoder) {
        stateEncoder.write(this, stateMapper.apply(value));
        return this;
    }

    public <T extends Enum<T>, S> T readEnum(CallableDecoder<? extends S> stateDecoder, Function<S, T> mapper) {
        return mapper.apply(stateDecoder.read(this));
    }

    public PacketBuffer writeUuid(UUID uuid) {
        writeVarLong(uuid.getMostSignificantBits());
        return writeVarLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        if (this.nettyBuffer.readableBytes() < 16)
            throw new PacketDecodeException("Not enough readableBytes to read UUID: " + readableBytes() + " / 16").runtime();
        return new UUID(readVarLong(), readVarLong());
    }

    public PacketBuffer writeByteArray(byte... bytes) {
        writeVarInt(bytes.length);
        return writeBytes(bytes);
    }

    public byte[] readByteArray() {
        int len = readVarInt();
        byte[] bytes = new byte[len];
        this.nettyBuffer.readBytes(bytes);
        return bytes;
    }

    public PacketBuffer writeBooleanArray(boolean... array) {
        writeVarInt(array.length);
        for (boolean i : array) writeBoolean(i);
        return this;
    }

    public boolean[] readBooleanArray() {
        int len = readVarInt();
        boolean[] arr = new boolean[len];
        for (int i = 0; i < len; i++) arr[i] = readBoolean();
        return arr;
    }

    public PacketBuffer writeCharArray(char... array) {
        writeVarInt(array.length);
        for (char i : array) writeChar(i);
        return this;
    }

    public char[] readCharArray() {
        int len = readVarInt();
        char[] arr = new char[len];
        for (int i = 0; i < len; i++) arr[i] = readChar();
        return arr;
    }

    public PacketBuffer writeIntArray(int... array) {
        writeVarInt(array.length);
        for (int i : array) writeInt(i);
        return this;
    }

    public int[] readIntArray() {
        int len = readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readInt();
        return arr;
    }

    public PacketBuffer writeVarIntArray(int... array) {
        writeVarInt(array.length);
        for (int i : array) writeVarInt(i);
        return this;
    }

    public int[] readVarIntArray() {
        int len = readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readVarInt();
        return arr;
    }

    public PacketBuffer writeShortArray(short... array) {
        writeVarInt(array.length);
        for (short i : array) writeShort(i);
        return this;
    }

    public short[] readShortArray() {
        int len = readVarInt();
        short[] arr = new short[len];
        for (int i = 0; i < len; i++) arr[i] = readShort();
        return arr;
    }

    public PacketBuffer writeFloatArray(float... array) {
        writeVarInt(array.length);
        for (float i : array) writeFloat(i);
        return this;
    }

    public float[] readFloatArray() {
        int len = readVarInt();
        float[] arr = new float[len];
        for (int i = 0; i < len; i++) arr[i] = readFloat();
        return arr;
    }

    public PacketBuffer writeDoubleArray(double... array) {
        writeVarInt(array.length);
        for (double i : array) writeDouble(i);
        return this;
    }

    public double[] readDoubleArray() {
        int len = readVarInt();
        double[] arr = new double[len];
        for (int i = 0; i < len; i++) arr[i] = readDouble();
        return arr;
    }

    public PacketBuffer writeVarLongArray(long... array) {
        writeVarInt(array.length);
        for (long i : array) writeVarLong(i);
        return this;
    }

    public long[] readVarLongArray() {
        int len = readVarInt();
        long[] arr = new long[len];
        for (int i = 0; i < len; i++) arr[i] = readVarLong();
        return arr;
    }

    private <T> boolean writeNullCheck(T object) {
        boolean notNull = object != null;
        writeBoolean(notNull);
        return !notNull;
    }

    public <T> PacketBuffer writeObject(@Nullable T object, CallableEncoder<? super T> encoder) {
        if (writeNullCheck(object)) return this;
        encoder.write(this, object);
        return this;
    }

    public <T> T readObject(CallableDecoder<? extends T> decoder) {
        if (!readBoolean()) return null;
        return decoder.read(this);
    }

    public <T> Optional<T> readOptional(CallableDecoder<? extends T> decoder) {
        if (!readBoolean()) return Optional.empty();
        return Optional.of(decoder.read(this));
    }

    public <T> PacketBuffer writeOptional(final Optional<T> optional, CallableEncoder<? super T> encoder) {
        optional.ifPresentOrElse(value -> {
            writeBoolean(true);
            encoder.write(this, value);
        }, () -> writeBoolean(false));

        return this;
    }

    public <T extends Encoder> PacketBuffer writeObject(T object) {
        return writeObject(object, (buffer, t) -> t.write(buffer));
    }

    public <T extends Decoder> T readObject(Supplier<T> factory) {
        return readObject(buffer -> {
            T obj = factory.get();
            obj.read(buffer);
            return obj;
        });
    }

    public <T> PacketBuffer writeIterable(Iterable<T> iterable, int size, CallableEncoder<? super T> encoder) {
        if (writeNullCheck(iterable)) return this;
        writeVarInt(size);
        for (T entry : iterable) {
            writeObject(entry, encoder);
        }
        return this;
    }

    @SafeVarargs
    public final <T> PacketBuffer writeArray(CallableEncoder<? super T> encoder, T... array) {
        if (writeNullCheck(array)) return this;
        writeVarInt(array.length);
        for (T entry : array) writeObject(entry, encoder);
        return this;
    }

    public <T> PacketBuffer writeCollection(Collection<T> collection, CallableEncoder<? super T> encoder) {
        return writeIterable(collection, collection.size(), encoder);
    }

    public <T extends Encoder> PacketBuffer writeCollection(Collection<T> collection) {
        return writeCollection(collection, (buffer, t) -> t.write(buffer));
    }

    public <T, C extends Collection<T>> C readCollection(CallableDecoder<? extends T> decoder, IntFunction<C> collectionFactory) {
        if (!readBoolean()) return null;
        final int size = readVarInt();
        final C collection = collectionFactory.apply(size);
        for (int i = 0; i < size; i++) collection.add(readObject(decoder));
        return collection;
    }

    public <T> List<T> readList(CallableDecoder<? extends T> decoder) {
        return readCollection(decoder, ArrayList::new);
    }

    public <T> T[] readArray(CallableDecoder<? extends T> decoder, IntFunction<T[]> arrayFactory) {
        if (!readBoolean()) return null;
        int size = readVarInt();
        T[] array = arrayFactory.apply(size);
        for (int i = 0; i < size; i++) {
            array[i] = readObject(decoder);
        }
        return array;
    }

    public <T extends Decoder> List<T> readCollection(Supplier<T> factory) {
        return readList(buffer -> buffer.readObject(factory));
    }

    public <K, V> PacketBuffer writeMap(Map<K, V> map, CallableEncoder<Pair<K, V>> encoder) {
        if (writeNullCheck(map)) return this;
        writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            writeObject(Pair.of(entry.getKey(), entry.getValue()), encoder);
        }
        return this;
    }

    public <K, V> Map<K, V> readMap(CallableDecoder<Pair<K, V>> decoder, IntFunction<Map<K, V>> mapFactory) {
        if (!readBoolean()) return null;

        int size = readVarInt();
        Map<K, V> map = mapFactory.apply(size);
        for (int i = 0; i < size; i++) {
            Pair<K, V> pair = readObject(decoder);
            map.put(pair.key(), pair.value());
        }
        return map;
    }

    public <K, V> PacketBuffer writeMap(Map<K, V> map, CallableEncoder<? super K> kEncoder, CallableEncoder<? super V> vEncoder) {
        return writeMap(map, (buffer, data) -> {
            kEncoder.write(buffer, data.key());
            vEncoder.write(buffer, data.value());
        });
    }

    public <K, V> Map<K, V> readMap(CallableDecoder<K> kDecoder, CallableDecoder<V> vDecoder, IntFunction<Map<K, V>> mapFactory) {
        return readMap(buffer -> Pair.of(kDecoder.read(buffer), vDecoder.read(buffer)), mapFactory);
    }

    public boolean release() {
        return this.nettyBuffer.release();
    }

    public PacketBuffer retain(int increment) {
        this.nettyBuffer.retain(increment);
        return this;
    }

    public PacketBuffer retain() {
        this.nettyBuffer.retain();
        return this;
    }

    public int refCnt() {
        return this.nettyBuffer.refCnt();
    }

    public byte[] getBytes() {
        byte[] bytes = new byte[readableBytes()];
        this.nettyBuffer.getBytes(this.nettyBuffer.readerIndex(), bytes);
        return bytes;
    }

    public byte[] readAllBytes() {
        byte[] bytes = new byte[readableBytes()];
        readBytes(bytes);
        return bytes;
    }

    public int readableBytes() {
        return this.nettyBuffer.readableBytes();
    }

    public PacketBuffer resetReaderIndex() {
        this.nettyBuffer.resetReaderIndex();
        return this;
    }

    public PacketBuffer markReaderIndex() {
        this.nettyBuffer.markReaderIndex();
        return this;
    }

    public int readerIndex() {
        return this.nettyBuffer.readerIndex();
    }

    public PacketBuffer readerIndex(int readerIndex) {
        this.nettyBuffer.readerIndex(readerIndex);
        return this;
    }

    public PacketBuffer resetWriterIndex() {
        this.nettyBuffer.resetWriterIndex();
        return this;
    }

    public PacketBuffer markWriterIndex() {
        this.nettyBuffer.markWriterIndex();
        return this;
    }

    public int writerIndex() {
        return this.nettyBuffer.writerIndex();
    }

    public PacketBuffer writerIndex(int writerIndex) {
        this.nettyBuffer.writerIndex(writerIndex);
        return this;
    }

    public PacketBuffer readBytes(byte[] data) {
        this.nettyBuffer.readBytes(data);
        return this;
    }

    public PacketBuffer writeBytes(byte[] data) {
        this.nettyBuffer.writeBytes(data);
        return this;
    }

    public PacketBuffer writeBytes(byte[] data, int offset, int length) {
        this.nettyBuffer.writeBytes(data, offset, length);
        return this;
    }

    // Write bytes from another ByteBuf without converting to byte[] externally
    public PacketBuffer writeBytes(ByteBuf src) {
        this.nettyBuffer.writeBytes(src);
        return this;
    }

    public PacketBuffer wrapData(Encoder encoder) {
        byte[] bytes = readAllBytes();
        encoder.write(this);
        return writeBytes(bytes);
    }

    // Prefer zero-copy when possible
    public PacketBuffer writeBuffer(PacketBuffer other) {
        // Avoid other.getBytes(); write directly from underlying ByteBuf
        this.nettyBuffer.writeBytes(other.nettyBuffer);
        return this;
    }

    public PacketBuffer readSlice(int length) {
        return new PacketBuffer(nettyBuffer.readSlice(length));
    }

    // Return a retained slice to safely pass across components without immediate copy
    public PacketBuffer readRetainedSlice(int length) {
        ByteBuf slice = nettyBuffer.readSlice(length);
        slice.retain();
        return new PacketBuffer(slice);
    }

    public PacketBuffer slice() {
        return new PacketBuffer(this.nettyBuffer.slice());
    }

    // Create a slice view from current readerIndex with specified length
    public PacketBuffer slice(int index, int length) {
        return new PacketBuffer(this.nettyBuffer.slice(index, length));
    }

    // Retained slice for longer-lived sharing
    public PacketBuffer retainedSlice(int index, int length) {
        ByteBuf slice = this.nettyBuffer.slice(index, length);
        slice.retain();
        return new PacketBuffer(slice);
    }

    public ByteBuf nettyBuffer() {
        return this.nettyBuffer;
    }

    public PacketBuffer writePercentual(float percent, float scale) {
        return this.writeVarInt((int) (MathUtils.clamp(percent) * scale));
    }

    public float readPercentual(float scale) {
        return this.readVarInt() / scale;
    }
}