package dev.sweety.netty.packet.model;

import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;

public class BatchPacket extends Packet {

    private final Batch batch;

    public BatchPacket(final CallableEncoder<Packet> encode, final Packet... packets) {
        (this.batch = new Batch(this.buffer())).encode(encode, packets);
    }

    public BatchPacket(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.batch = new Batch(this.buffer());
    }


    public Packet[] decode(CallableDecoder<Packet> decoder) {
        return this.batch.decode(decoder);
    }
}
