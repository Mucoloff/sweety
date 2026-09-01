package dev.sweety.netty.packet.buffer;

import dev.sweety.data.buffer.AbstractBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;
import dev.sweety.math.pool.Pooled;
import dev.sweety.math.pool.Release;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

@Pooled(pool = PacketBufferAllocator.class)
public class PacketBuffer extends AbstractBuffer<PacketBuffer> {

    static final int DEFAULT_CAPACITY = 256;

    private ByteBuf nettyBuffer;
    private final Consumer<PacketBuffer> recycler;

    public PacketBuffer(ByteBuf nettyBuffer) {
        this.nettyBuffer = nettyBuffer;
        this.recycler = null;
    }

    public PacketBuffer(int capacity) {
        this(PooledByteBufAllocator.DEFAULT.buffer(capacity));
    }

    public PacketBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public PacketBuffer(byte[] bytes) {
        this(Unpooled.wrappedBuffer(bytes));
    }

    /** Marker for the no-alloc wrapper ctor. */
    private enum Wrapper { INSTANCE }

    /** No-alloc ctor: empty view, no underlying buffer. Use only with {@link #wrapExternal(ByteBuf)}. */
    private PacketBuffer(Wrapper ignored) {
        this.nettyBuffer = null;
        this.recycler = null;
    }

    /** Creates a wrapper with no backing buffer; must be repointed via {@link #wrapExternal(ByteBuf)} before use. */
    public static PacketBuffer wrapper() {
        return new PacketBuffer(Wrapper.INSTANCE);
    }

    /**
     * Pool ctor — used by {@link PacketBufferAllocator}.
     */
    PacketBuffer(int capacity, Consumer<PacketBuffer> recycler) {
        this.nettyBuffer = PooledByteBufAllocator.DEFAULT.buffer(capacity);
        this.recycler = recycler;
    }

    @Override
    protected void poolReset() {
        if (nettyBuffer == null) {
            nettyBuffer = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_CAPACITY);
        } else {
            nettyBuffer.clear();
        }
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
    }

    /**
     * Repoints this wrapper to an externally-owned {@link ByteBuf} without acquiring a new
     * underlying buffer. Intended for thread-local wrapper reuse: the caller retains ownership of
     * {@code buf} and must manage its lifecycle; this object is only a view for the call duration.
     */
    public PacketBuffer wrapExternal(ByteBuf buf) {
        this.nettyBuffer = buf;
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
        return this;
    }

    @Override
    public void clear() {
        this.nettyBuffer.clear();
        resetPackedBooleanReadState();
        resetPackedBooleanWriteState();
    }

    @Override
    public PacketBuffer discardReadBytes() {
        this.nettyBuffer.discardReadBytes();
        resetPackedBooleanReadState();
        // Content shifts left here — any in-progress write mask byte's absolute writePosIndex
        // goes stale, so a pending partial group would corrupt whatever now sits at that offset.
        // Force the next writeBoolean() to start a fresh group, matching writerIndex(int)/resetWriterIndex().
        resetPackedBooleanWriteState();
        return this;
    }

    @Override
    public int capacity() {
        return this.nettyBuffer.capacity();
    }

    @Override
    public int writableBytes() {
        return this.nettyBuffer.writableBytes();
    }

    @Override
    public PacketBuffer ensureWritable(int minWritableBytes) {
        this.nettyBuffer.ensureWritable(minWritableBytes);
        return this;
    }

    //use writeVarInt
    @Override
    @Deprecated
    public PacketBuffer writeInt(int value) {
        this.nettyBuffer.writeInt(value);
        return this;
    }

    //use readVarInt
    @Override
    @Deprecated
    public int readInt() {
        return this.nettyBuffer.readInt();
    }

    @Override
    public PacketBuffer writeDouble(double value) {
        this.nettyBuffer.writeDouble(value);
        return this;
    }

    @Override
    public double readDouble() {
        return this.nettyBuffer.readDouble();
    }

    @Override
    public PacketBuffer writeShort(short value) {
        this.nettyBuffer.writeShort(value);
        return this;
    }

    @Override
    public short readShort() {
        return this.nettyBuffer.readShort();
    }

    @Override
    public PacketBuffer writeByte(byte value) {
        this.nettyBuffer.writeByte(value);
        return this;
    }

    @Override
    public byte readByte() {
        return this.nettyBuffer.readByte();
    }

    @Override
    public PacketBuffer writeChar(char value) {
        this.nettyBuffer.writeChar(value);
        return this;
    }

    @Override
    public char readChar() {
        return this.nettyBuffer.readChar();
    }

    @Override
    public PacketBuffer writeFloat(float value) {
        this.nettyBuffer.writeFloat(value);
        return this;
    }

    @Override
    public float readFloat() {
        return this.nettyBuffer.readFloat();
    }

    //use writeVarLong
    @Override
    @Deprecated
    public PacketBuffer writeLong(long value) {
        this.nettyBuffer.writeLong(value);
        return this;
    }

    //use readVarLong
    @Override
    @Deprecated
    public long readLong() {
        return this.nettyBuffer.readLong();
    }

    @Override
    public short readUnsignedByte() {
        return this.nettyBuffer.readUnsignedByte();
    }

    @Release
    @Override
    public boolean release() {
        boolean released = nettyBuffer.release();
        if (released && recycler != null) {
            nettyBuffer = null;
            recycler.accept(this);
        }
        return released;
    }

    @Override
    public PacketBuffer retain(int increment) {
        this.nettyBuffer.retain(increment);
        return this;
    }

    @Override
    public PacketBuffer retain() {
        this.nettyBuffer.retain();
        return this;
    }

    @Override
    public int refCnt() {
        return this.nettyBuffer.refCnt();
    }

    @Override
    public int readableBytes() {
        return this.nettyBuffer.readableBytes();
    }

    @Override
    public PacketBuffer resetReaderIndex() {
        this.nettyBuffer.resetReaderIndex();
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public PacketBuffer markReaderIndex() {
        this.nettyBuffer.markReaderIndex();
        return this;
    }

    @Override
    public int readerIndex() {
        return this.nettyBuffer.readerIndex();
    }

    @Override
    public PacketBuffer readerIndex(int readerIndex) {
        this.nettyBuffer.readerIndex(readerIndex);
        resetPackedBooleanReadState();
        return this;
    }

    @Override
    public PacketBuffer resetWriterIndex() {
        this.nettyBuffer.resetWriterIndex();
        resetPackedBooleanWriteState();
        return this;
    }

    @Override
    public PacketBuffer markWriterIndex() {
        this.nettyBuffer.markWriterIndex();
        return this;
    }

    @Override
    public int writerIndex() {
        return this.nettyBuffer.writerIndex();
    }

    @Override
    public PacketBuffer writerIndex(int writerIndex) {
        this.nettyBuffer.writerIndex(writerIndex);
        resetPackedBooleanWriteState();
        return this;
    }

    @Override
    public PacketBuffer readBytes(byte[] data) {
        this.nettyBuffer.readBytes(data);
        return this;
    }

    @Override
    public PacketBuffer readBytes(byte[] data, int offset, int length) {
        this.nettyBuffer.readBytes(data, offset, length);
        return this;
    }

    @Override
    public PacketBuffer writeBytes(byte[] data) {
        this.nettyBuffer.writeBytes(data);
        return this;
    }

    @Override
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
    @Override
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

    /**
     * Wraps a {@link ByteBuffer} as a {@link PacketBuffer} (zero-copy via Netty's {@code Unpooled.wrappedBuffer}).
     */
    public static PacketBuffer wrap(ByteBuffer bb) {
        return new PacketBuffer(Unpooled.wrappedBuffer(bb));
    }

    /**
     * Returns a NIO {@link ByteBuffer} view of the readable bytes.
     * Backed by the same memory — use with care when the underlying ByteBuf is direct.
     */
    public ByteBuffer asNioBuffer() {
        return this.nettyBuffer.nioBuffer();
    }

    @Override
    public PacketBuffer setByte(int index, byte value) {
        this.nettyBuffer.setByte(index, value);
        return this;
    }

    @Override
    public byte getByte(int index) {
        return this.nettyBuffer.getByte(index);
    }

    @Override
    public PacketBuffer setShort(int index, short value) {
        this.nettyBuffer.setShort(index, value);
        return this;
    }

    @Override
    public short getShort(int index) {
        return this.nettyBuffer.getShort(index);
    }

    @Override
    public PacketBuffer setInt(int index, int value) {
        this.nettyBuffer.setInt(index, value);
        return this;
    }


    @Override
    public int getInt(int index) {
        return this.nettyBuffer.getInt(index);
    }

    @Override
    public PacketBuffer setLong(int index, long value) {
        this.nettyBuffer.setLong(index, value);
        return this;
    }

    @Override
    public long getLong(int index) {
        return this.nettyBuffer.getLong(index);
    }


    @Override
    public PacketBuffer setFloat(int index, float value) {
        this.nettyBuffer.setFloat(index, value);
        return this;
    }


    @Override
    public float getFloat(int index) {
        return this.nettyBuffer.getFloat(index);
    }

    public PacketBuffer setDouble(int index, double value) {
        this.nettyBuffer.setDouble(index, value);
        return this;
    }


    @Override
    public double getDouble(int index) {
        return this.nettyBuffer.getDouble(index);
    }


    @Override
    public PacketBuffer setChar(int index, char value) {
        this.nettyBuffer.setChar(index, value);
        return this;
    }


    @Override
    public char getChar(int index) {
        return this.nettyBuffer.getChar(index);
    }

    @Override
    public boolean isReadable() {
        return this.nettyBuffer.isReadable();
    }


    @Override
    protected void getBytes(int index, byte[] dst) {
        this.nettyBuffer.getBytes(index, dst);
    }


    public <T extends Enum<T>, S> PacketBuffer writeEnum(T value, Function<T, S> stateMapper, CallableEncoder<? super S> stateEncoder) {
        super.writeEnum(value, stateMapper, stateEncoder);
        return this;
    }


    public <T extends Enum<T>, S> T readEnum(CallableDecoder<? extends S> stateDecoder, Function<S, T> mapper) {
        return super.readEnum(stateDecoder, mapper);
    }


    public <T> PacketBuffer writeObject(@Nullable T object, CallableEncoder<? super T> encoder) {
        super.writeObject(object, encoder);
        return this;
    }


    public <T> T readObject(CallableDecoder<? extends T> decoder) {
        return super.readObject(decoder);
    }


    public <T> Optional<T> readOptional(CallableDecoder<? extends T> decoder) {
        return super.readOptional(decoder);
    }


    public <T> PacketBuffer writeIterable(Iterable<T> iterable, int size, CallableEncoder<? super T> encoder) {
        super.writeIterable(iterable, size, encoder);
        return this;
    }


    public <T> PacketBuffer writeCollection(Collection<T> collection, CallableEncoder<? super T> encoder) {
        super.writeCollection(collection, encoder);
        return this;
    }


    public <T, C extends Collection<T>> C readCollection(CallableDecoder<? extends T> decoder, IntFunction<C> collectionFactory) {
        return super.readCollection(decoder, collectionFactory);
    }


    public <T> List<T> readList(CallableDecoder<? extends T> decoder) {
        return super.readList(decoder);
    }


    public <T> T[] readArray(CallableDecoder<? extends T> decoder, IntFunction<T[]> arrayFactory) {
        return super.readArray(decoder, arrayFactory);
    }

    public <K, V> PacketBuffer writeMap(Map<K, V> map, CallableEncoder<Pair<K, V>> encoder) {
        super.writeMap(map, encoder);
        return this;
    }


    public <K, V> Map<K, V> readMap(CallableDecoder<Pair<K, V>> decoder, IntFunction<Map<K, V>> mapFactory) {
        return super.readMap(decoder, mapFactory);
    }

    public <K, V> PacketBuffer writeMap(Map<K, V> map, CallableEncoder<? super K> kEncoder, CallableEncoder<? super V> vEncoder) {
        super.writeMap(map, kEncoder, vEncoder);
        return this;
    }

    public <K, V> Map<K, V> readMap(CallableDecoder<K> kDecoder, CallableDecoder<V> vDecoder, IntFunction<Map<K, V>> mapFactory) {
        return super.readMap(kDecoder, vDecoder, mapFactory);
    }

    public <K extends Enum<K>, V> EnumMap<K, V> readEnumMap(Class<K> keyClass, CallableDecoder<V> vDecoder) {
        return super.readEnumMap(keyClass, vDecoder);
    }

    public <K extends Enum<K>, V> EnumMap<K, V> readMap(Class<K> keyClass, CallableDecoder<V> vDecoder) {
        return super.readMap(keyClass, vDecoder);
    }

    public <E extends Enum<E>> java.util.EnumSet<E> readCollection(Class<E> type) {
        return super.readCollection(type);
    }

    public <K extends Enum<K>, V> PacketBuffer writeEnumMap(EnumMap<K, V> map, CallableEncoder<? super V> vEncoder) {
        super.writeEnumMap(map, vEncoder);
        return this;
    }

    public <V> Int2ObjectOpenHashMap<V> readInt2ObjectMap(CallableDecoder<V> vDecoder) {
        return super.readInt2ObjectMap(vDecoder);
    }

    public <V> Long2ObjectOpenHashMap<V> readLong2ObjectMap(CallableDecoder<V> vDecoder) {
        return super.readLong2ObjectMap(vDecoder);
    }

    public <K> Object2IntOpenHashMap<K> readObject2IntMap(CallableDecoder<K> kDecoder) {
        return super.readObject2IntMap(kDecoder);
    }

    public <K> Object2LongOpenHashMap<K> readObject2LongMap(CallableDecoder<K> kDecoder) {
        return super.readObject2LongMap(kDecoder);
    }

    public IntOpenHashSet readIntSet() {
        return super.readIntSet();
    }

    public IntArrayList readIntList() {
        return super.readIntList();
    }

    public LongOpenHashSet readLongSet() {
        return super.readLongSet();
    }

    public LongArrayList readLongList() {
        return super.readLongList();
    }

}