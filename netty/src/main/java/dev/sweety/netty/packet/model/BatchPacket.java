package dev.sweety.netty.packet.model;

import dev.sweety.core.math.TriFunction;
import dev.sweety.netty.feature.batch.Batch;

import java.util.function.Function;

public class BatchPacket extends Packet {

    private final Batch batch;

    public BatchPacket(final Function<Class<? extends Packet>, Integer> id, final Packet... packets) {
        this.batch = new Batch(id, p -> p instanceof BatchPacket, packets);
        this.batch.write(this.buffer());
    }

    int readerIndex;

    public BatchPacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
        this.readerIndex = this.buffer().readerIndex();
        this.batch = new Batch();
    }

    public Packet[] decode(final TriFunction<Packet, Integer, Long, byte[]> constructor) {
        this.buffer().readerIndex(this.readerIndex);
        return this.batch.decode(constructor);
    }
}
