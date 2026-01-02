package dev.sweety.netty.packet.buffer;

import dev.sweety.netty.messaging.exception.*;
import dev.sweety.netty.packet.buffer.io.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.zip.CRC32;

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

    public void writeInt(int value) {
        this.nettyBuffer.writeInt(value);
    }

    public int readInt() {
        return this.nettyBuffer.readInt();
    }

    public void writeVarInt(int value) {
        while ((value & 0xFFFFFF80) != 0L) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        writeByte((byte) (value & 0x7F));
    }

    public int readVarInt() {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) throw new PacketDecodeException("VarInt too big").toRuntime();
        } while ((read & 0x80) != 0);

        return result;
    }

    public void writeDouble(double value) {
        this.nettyBuffer.writeDouble(value);
    }

    public double readDouble() {
        return this.nettyBuffer.readDouble();
    }

    public void writeShort(short value) {
        this.nettyBuffer.writeShort(value);
    }

    public short readShort() {
        return this.nettyBuffer.readShort();
    }

    public void writeByte(byte value) {
        this.nettyBuffer.writeByte(value);
    }

    public byte readByte() {
        return this.nettyBuffer.readByte();
    }

    /*
    public void writeBoolean(boolean value) {
        this.nettyBuffer.writeByte(value ? 1 : 0);
    }

    public boolean readBoolean() {
        return this.nettyBuffer.readByte() == 1;
    }
     */

    private byte _mask = 0, _maskIndex = 0;
    private int _posIndex = 0;

    public void writeBoolean(boolean value) {
        if (_maskIndex % 8 == 0) {
            _posIndex = nettyBuffer.writerIndex();
            nettyBuffer.writeByte(_mask = 0);
        }

        if (value) _mask |= (byte) (1 << (_maskIndex % 8));

        nettyBuffer.setByte(_posIndex, _mask);
        _maskIndex++;
    }

    public boolean readBoolean() {
        if (_maskIndex % 8 == 0) {
            if (!nettyBuffer.isReadable())
                throw new PacketDecodeException("Unable to read boolean", new EOFException()).toRuntime();
            _mask = nettyBuffer.readByte();
        }

        return ((_mask >> (_maskIndex++ % 8)) & 1) != 0;
    }

    public void writeFloat(float value) {
        this.nettyBuffer.writeFloat(value);
    }

    public float readFloat() {
        return this.nettyBuffer.readFloat();
    }

    public void writeLong(long value) {
        this.nettyBuffer.writeLong(value);
    }

    public long readLong() {
        return this.nettyBuffer.readLong();
    }

    public short readUnsignedByte() {
        return this.nettyBuffer.readUnsignedByte();
    }

    public void writeString(String data, Charset charset) {
        if (data == null) {
            writeVarInt(0);
            return;
        }

        byte[] bytes = data.getBytes(charset);
        writeVarInt(bytes.length);
        nettyBuffer.writeBytes(bytes);
    }


    public String readString(Charset charset) {
        int length = readVarInt();

        if (length < 0) throw new PacketDecodeException("Invalid string length: " + length).toRuntime();
        if (nettyBuffer.readableBytes() < length)
            throw new IndexOutOfBoundsException(
                    "Not enough bytes to read string: requested " + length + ", available " + nettyBuffer.readableBytes());

        byte[] bytes = new byte[length];
        nettyBuffer.readBytes(bytes);
        return new String(bytes, charset);
    }

    public void writeString(String data) {
        writeString(data, StandardCharsets.UTF_8);
    }


    public String readString() {
        return readString(StandardCharsets.UTF_8);
    }


    public void writeEnum(Enum<?> value) {
        writeVarInt(value.ordinal());
    }

    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        int ordinal = this.readVarInt();
        if (ordinal >= 0 && ordinal < constants.length) return constants[ordinal];
        else throw new PacketDecodeException("Invalid enum ordinal: " + ordinal).toRuntime();
    }

    public void writeUuid(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        if (this.nettyBuffer.readableBytes() < 16)
            throw new PacketDecodeException("Not enough readableBytes to read UUID: " + readableBytes() + " / 16").toRuntime();
        return new UUID(readLong(), readLong());
    }

    public void writeBytesArray(byte[] bytes) {
        writeVarInt(bytes.length);
        writeBytes(bytes);
    }

    public byte[] readBytesArray() {
        int len = readVarInt();
        byte[] bytes = new byte[len];
        this.nettyBuffer.readBytes(bytes);
        return bytes;
    }

    public void writeBooleanArray(boolean[] array) {
        writeVarInt(array.length);
        for (boolean i : array) writeBoolean(i);
    }

    public boolean[] readBooleanArray() {
        int len = readVarInt();
        boolean[] arr = new boolean[len];
        for (int i = 0; i < len; i++) arr[i] = readBoolean();
        return arr;
    }

    public void writeIntArray(int[] array) {
        writeVarInt(array.length);
        for (int i : array) writeInt(i);
    }

    public int[] readIntArray() {
        int len = readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readInt();
        return arr;
    }

    public void writeVarIntArray(int[] array) {
        writeVarInt(array.length);
        for (int i : array) writeVarInt(i);
    }

    public int[] readVarIntArray() {
        int len = readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = readVarInt();
        return arr;
    }

    public void writeShortArray(short[] array) {
        writeVarInt(array.length);
        for (short i : array) writeShort(i);
    }

    public short[] readShortArray() {
        int len = readVarInt();
        short[] arr = new short[len];
        for (int i = 0; i < len; i++) arr[i] = readShort();
        return arr;
    }

    public void writeFloatArray(float[] array) {
        writeVarInt(array.length);
        for (float i : array) writeFloat(i);
    }

    public float[] readFloatArray() {
        int len = readVarInt();
        float[] arr = new float[len];
        for (int i = 0; i < len; i++) arr[i] = readFloat();
        return arr;
    }


    private <T> boolean writeNullCheck(T object) {
        boolean notNull = object != null;
        writeBoolean(notNull);
        return !notNull;
    }

    public <T> void writeObject(@Nullable T object, CallableEncoder<? super T> encoder) {
        if (writeNullCheck(object)) return;
        encoder.write(this, object);
    }

    public <T> T readObject(CallableDecoder<? extends T> decoder) {
        if (!readBoolean()) return null;
        return decoder.read(this);
    }

    public <T> Optional<T> readOptional(CallableDecoder<? extends T> decoder) {
        return Optional.ofNullable(readObject(decoder));
    }

    public <T extends Encoder> void writeObject(T object) {
        writeObject(object, (buffer, t) -> t.write(buffer));
    }

    public <T extends Decoder> T readObject(Supplier<T> factory) {
        return readObject(buffer -> {
            T obj = factory.get();
            obj.read(buffer);
            return obj;
        });
    }

    private <T> void writeIterable(
            Iterable<T> iterable,
            int size,
            CallableEncoder<? super T> encoder
    ) {
        if (writeNullCheck(iterable)) return;
        writeVarInt(size);
        for (T entry : iterable) {
            writeObject(entry, encoder);
        }
    }

    @SafeVarargs
    public final <T> void writeArray(CallableEncoder<? super T> encoder, T... array) {
        if (writeNullCheck(array)) return;
        writeVarInt(array.length);
        for (T entry : array) {
            writeObject(entry, encoder);
        }
    }

    public <T> void writeCollection(Collection<T> collection, CallableEncoder<? super T> encoder) {
        writeIterable(collection, collection.size(), encoder);
    }

    public <T extends Encoder> void writeCollection(Collection<T> collection) {
        writeCollection(collection, (buffer, t) -> t.write(buffer));
    }

    public <T> List<T> readCollection(CallableDecoder<? extends T> decoder) {
        if (!readBoolean()) return null;
        int size = readVarInt();
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(readObject(decoder));
        }
        return list;
    }

    public <T, C extends Collection<T>> C readCollection(
            CallableDecoder<? extends T> decoder,
            Supplier<C> collectionFactory) {

        if (!readBoolean()) return null;
        int size = readVarInt();
        C collection = collectionFactory.get();
        for (int i = 0; i < size; i++) {
            collection.add(readObject(decoder));
        }
        return collection;
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
        return readCollection(buffer -> buffer.readObject(factory));
    }

    public <K, V> void writeMap(Map<K, V> map,
                                CallableEncoder<Pair<K, V>> encoder) {
        if (writeNullCheck(map)) return;
        writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            writeObject(Pair.of(entry.getKey(), entry.getValue()), encoder);
        }
    }

    public <K, V> Map<K, V> readMap(CallableDecoder<Pair<K, V>> decoder,
                                    Supplier<Map<K, V>> mapFactory) {
        if (!readBoolean()) return null;

        int size = readVarInt();
        Map<K, V> map = mapFactory.get();
        for (int i = 0; i < size; i++) {
            Pair<K, V> pair = readObject(decoder);
            map.put(pair.key(), pair.value());
        }
        return map;
    }

    public <K, V> void writeMap(Map<K, V> map,
                                CallableEncoder<? super K> kEncoder,
                                CallableEncoder<? super V> vEncoder) {
        writeMap(map, (buffer, data) -> {
            kEncoder.write(buffer, data.key());
            vEncoder.write(buffer, data.value());
        });
    }

    public <K, V> Map<K, V> readMap(CallableDecoder<K> kDecoder,
                                    CallableDecoder<V> vDecoder,
                                    Supplier<Map<K, V>> mapFactory) {

        return readMap(buffer -> Pair.of(kDecoder.read(buffer), vDecoder.read(buffer)), mapFactory);
    }

    public void release() {
        this.nettyBuffer.release();
    }

    public byte[] getBytes() {
        byte[] bytes = new byte[readableBytes()];
        readBytes(bytes);
        return bytes;
    }

    public int readableBytes() {
        return this.nettyBuffer.readableBytes();
    }

    public void resetReaderIndex() {
        this.nettyBuffer.resetReaderIndex();
    }

    public void markReaderIndex() {
        this.nettyBuffer.markReaderIndex();
    }

    public void resetWriterIndex() {
        this.nettyBuffer.resetWriterIndex();
    }

    public void markWriterIndex() {
        this.nettyBuffer.markWriterIndex();
    }

    public void readBytes(byte[] data) {
        this.nettyBuffer.readBytes(data);
    }

    public void writeBytes(byte[] data) {
        this.nettyBuffer.writeBytes(data);
    }

    public void writeBytes(byte[] data, int offset, int length) {
        this.nettyBuffer.writeBytes(data, offset, length);
    }

    public void wrapData(Encoder encoder) {
        byte[] bytes = getBytes();
        encoder.write(this);
        writeBytes(bytes);
    }

    public void writeBuffer(PacketBuffer other) {
        writeBytes(other.getBytes());
    }

    public PacketBuffer readSlice(int length) {
        ByteBuf slice = nettyBuffer.readSlice(length);
        return new PacketBuffer(slice);
    }

    public ByteBuf nettyBuffer() {
        return this.nettyBuffer;
    }

    public void writeCRC32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        writeLong(crc.getValue());
    }

    public boolean verifyCRC32(byte[] data) {
        if (readableBytes() < 8) return false;
        long expected = readLong();
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return expected == crc.getValue();
    }


}

