package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.core.math.TriFunction;
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
    protected Forward constructRequest() {
        return new Forward();
    }

    @Override
    protected Forward constructResponse() {
        return new Forward();
    }

    public static class Forward extends PacketTransaction.Transaction {

        int packetCount;
        int[] packetIds;
        long[] packetTimestamps;
        byte[][] packetData;

        public Forward(Function<Class<? extends Packet>, Integer> idMap, Packet... packets) {
            if (packets == null || packets.length == 0) {
                this.packetCount = 0;
                this.packetIds = new int[0];
                this.packetTimestamps = new long[0];
                this.packetData = new byte[0][0];
                return;
            }

            this.packetCount = packets.length;
            this.packetIds = new int[this.packetCount];
            this.packetTimestamps = new long[this.packetCount];
            this.packetData = new byte[this.packetCount][];
            for (int i = 0; i < packetCount; i++) {
                Packet pkt = packets[i];
                packetIds[i] = idMap.apply(pkt.getClass());
                packetTimestamps[i] = pkt.timestamp();
                packetData[i] = pkt.buffer().getBytes();
            }
        }

        @Override
        public void write(PacketBuffer buffer) {
            buffer.writeVarInt(packetCount);
            for (int i = 0; i < packetCount; i++) {
                buffer.writeVarInt(packetIds[i]);
                buffer.writeVarLong(packetTimestamps[i]);
                buffer.writeByteArray(packetData[i]);
            }
        }

        public Forward() {

        }

        @Override
        public void read(PacketBuffer buffer) {
            this.packetCount = buffer.readVarInt();
            this.packetIds = new int[packetCount];
            this.packetTimestamps = new long[packetCount];
            this.packetData = new byte[packetCount][];
            for (int i = 0; i < packetCount; i++) {
                this.packetIds[i] = buffer.readVarInt();
                this.packetTimestamps[i] = buffer.readVarLong();
                this.packetData[i] = buffer.readByteArray();
            }
        }

        public Packet[] decode(TriFunction<Packet, Integer, Long, byte[]> constructor) {
            Packet[] packets = new Packet[packetCount];
            for (int i = 0; i < packetCount; i++) {
                packets[i] = constructor.apply(packetIds[i], packetTimestamps[i], packetData[i]);
            }
            return packets;
        }
    }

}
