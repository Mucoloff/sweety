package dev.sweety.netty.packet.model;

import dev.sweety.netty.packet.buffer.io.Codec;
import dev.sweety.time.TimeUtils;

public abstract class Packet implements Codec {

    private long _timestamp;

    public Packet() {
        this._timestamp = -1L;
    }

    public Packet(final long timestamp) {
        this._timestamp = timestamp;
    }

    public void assignTimestamp(final long timestamp) {
        this._timestamp = timestamp;
    }

    public String name() {
        return this.getClass().getSimpleName();
    }

    public long timestamp() {
        return this._timestamp;
    }

    public boolean hasTimestamp() {
        return this._timestamp > 0;
    }

    @Override
    public String toString() {
        return "%s%s".formatted(name(), _timestamp > 0 ? " [" + TimeUtils.date(_timestamp, "dd-mm-yyyy hh:MM:ss") + "]" : "");
    }
}
