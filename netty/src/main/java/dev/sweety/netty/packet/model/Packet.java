package dev.sweety.netty.packet.model;

import dev.sweety.core.time.TimeUtils;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import org.jetbrains.annotations.NotNull;

public abstract class Packet {

    private final short _id;
    private final long _timestamp;
    private @NotNull
    final PacketBuffer _buffer;

    public Packet() {
        this(-1L);
    }

    public Packet(long timestamp) {
        this._id = -1;
        this._timestamp = timestamp;
        this._buffer = new PacketBuffer();
    }

    // (decoder)
    private int _readerIndex;
    public Packet(short _id, long _timestamp, byte[] _data) {
        this._id = _id;
        this._timestamp = _timestamp;
        this._buffer = new PacketBuffer(_data);
        this._readerIndex = this._buffer.readerIndex();
    }

    public Packet rewind() {
        this._buffer.readerIndex(_readerIndex);
        return this;
    }

    public String name() {
        return this.getClass().getSimpleName();
    }

    public short id() {
        return this._id;
    }

    public long timestamp() {
        return this._timestamp;
    }

    public PacketBuffer buffer() {
        return this._buffer;
    }

    public int refCnt() {
        return this._buffer.refCnt();
    }

    public Packet retain() {
        this._buffer.retain();
        return this;
    }

    public Packet retain(int increment) {
        this._buffer.retain(increment);
        return this;
    }

    public boolean release() {
        try {
            // Avoid double-free: only release if refCnt > 0
            if (this._buffer.refCnt() > 0) {
                return this._buffer.release();
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean hasTimestamp() {
        return this._timestamp > 0;
    }

    @Override
    public String toString() {
        return name() + " (" + _id + ")" + (_timestamp > 0 ? (" [" + TimeUtils.date(_timestamp, "dd-mm-yyyy hh:MM:ss") + "] ") : " ") + "- " + _buffer.readableBytes() + " bytes";
    }

}
