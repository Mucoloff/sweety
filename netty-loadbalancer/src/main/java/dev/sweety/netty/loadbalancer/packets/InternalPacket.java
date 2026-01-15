package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;

public class InternalPacket extends PacketTransaction<InternalPacket.Forward, InternalPacket.Forward> {

    public InternalPacket(final Forward request) {
        super(request);
    }

    public InternalPacket(final long id, final Forward forward) {
        super(id, forward);
    }

    public InternalPacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected Forward constructRequest() {
        return new Forward();
    }

    @Override
    protected Forward constructResponse() {
        return new Forward();
    }


    public static class Forward extends PacketTransaction.Transaction {

        //write
        private CallableEncoder<Packet> encode;
        private Packet[] packets;

        public Forward(final PacketEncoder encoder, Packet... packets) {
            this.encode = encoder::sneakyEncode;
            this.packets = packets;
        }

        @Override
        public void write(PacketBuffer buffer) {
            new Batch(buffer).encode(this.encode, this.packets);
        }

        //read
        private Batch batch;

        public Forward() {
        }

        public void read(PacketBuffer buffer) {
            this.batch = new Batch(buffer);
        }

        public Packet[] decode(PacketDecoder decoder) {
            return this.batch.decode(decoder::sneakyDecode);
        }
    }

}
