package dev.sweety.netty.loadbalancer.common.packet;

import dev.sweety.core.math.function.TriFunction;
import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.function.Function;

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
    protected Forward request() {
        return new Forward();
    }

    @Override
    protected Forward response() {
        return new Forward();
    }

    public static class Forward extends PacketTransaction.Transaction {

        private final Batch batch;

        public Forward(Function<Class<? extends Packet>, Integer> idMap, Packet... packets) {
            this.batch = new Batch(idMap, p -> p instanceof InternalPacket, packets);
        }

        public Forward() {
            this.batch = new Batch();
        }

        @Override
        public void write(final PacketBuffer buffer) {
            this.batch.write(buffer);
        }

        @Override
        public void read(PacketBuffer buffer) {
            this.batch.read(buffer);
        }

        public Packet[] decode(TriFunction<Packet, Integer, Long, byte[]> constructor) {
            return this.batch.decode(constructor);
        }
    }

}
