package dev.sweety.netty.packet.model;

import dev.sweety.core.time.TimeUtils;
import dev.sweety.netty.packet.buffer.PacketBuffer;

public abstract class Packet {

    private final short _id;
    private final long _timestamp;
    private final PacketBuffer _buffer;

    public Packet() {
        this(-1L);
    }

    public Packet(long timestamp) {
        this._id = -1;
        this._timestamp = timestamp;
        this._buffer = new PacketBuffer();
    }

    // (decoder)
    public Packet(short _id, long _timestamp, byte[] _data) {
        this._id = _id;
        this._timestamp = _timestamp;
        this._buffer = new PacketBuffer(_data);
        this._buffer.markReaderIndex();
    }

    /*
    public byte[] internalBufferData() {
        return this._buffer.getBytes();
    }
     */

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

    public void release() {
        if (this._buffer != null) this._buffer.release();
    }

    public boolean hasTimestamp() {
        return this._timestamp > 0;
    }

    @Override
    public String toString() {
        return name() + " (" + _id + ")" + (_timestamp > 0 ? (" [" + TimeUtils.date(_timestamp, "dd-mm-yyyy hh:MM:ss") + "] ") : " ") + "- " + _buffer.readableBytes() + " bytes";
    }

}
