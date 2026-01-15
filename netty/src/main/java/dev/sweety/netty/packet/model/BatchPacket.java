package dev.sweety.netty.packet.model;

import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;

public class BatchPacket extends Packet {

    public BatchPacket(final CallableEncoder<Packet> encode, final Packet... packets) {
        Batch.encode(this.buffer(), encode, packets);
    }

    int readerIndex;

    public BatchPacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
        this.readerIndex = this.buffer().readerIndex();
    }

    public Packet[] decode(final CallableDecoder<Packet> decoder) {
        this.buffer().readerIndex(this.readerIndex);
        return Batch.decode(this.buffer(), decoder);
    }
}
