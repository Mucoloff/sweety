package dev.sweety.netty.packet.model;

import dev.sweety.math.function.TriFunction;
import dev.sweety.netty.feature.batch.Batch;

import java.util.function.Function;

public class BatchPacket extends Packet {

    private final Batch batch;

    public BatchPacket(final Function<Class<? extends Packet>, Integer> idMap, final Packet... packets) {
        this.batch = new Batch(idMap, p -> p instanceof BatchPacket, packets);
        this.batch.write(this.buffer());
    }

    public BatchPacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
        this.batch = new Batch();
        this.batch.read(this.buffer());
    }

    public Packet[] decode(final TriFunction<Packet, Integer, Long, byte[]> constructor) {
        if (this.batch == null) {
            return new Packet[0];
        }

        return this.batch.decode(constructor);
    }

    public byte[] rawBatchBytes() {
        return this.batch != null ? this.batch.rawBatchBytes() : new byte[0];
    }

    public boolean isDecoded() {
        return this.batch != null && this.batch.isDecoded();
    }
}
