package dev.sweety.netty.feature.batch;

import dev.sweety.core.math.function.TriFunction;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Decoder;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.model.Packet;

import java.util.LinkedList;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Batch implements Encoder, Decoder {

    int packetCount;
    int[] packetIds;
    long[] packetTimestamps;
    byte[][] packetData;

    public Batch(final Function<Class<? extends Packet>, Integer> idMap, final Predicate<Packet> exclusion, final Packet... packets) {
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

        LinkedList<Integer> toRemove = new LinkedList<>();
        for (int i = 0; i < packetCount; i++) {
            Packet pkt = packets[i];
            int id;
            if (pkt != null && (id = idMap.apply(pkt.getClass())) != -1 && !exclusion.test(pkt)) {
                packetIds[i] = id;
                packetTimestamps[i] = pkt.timestamp();
                packetData[i] = pkt.buffer().getBytes();
            } else {
                packetIds[i] = -1;
                packetTimestamps[i] = 0L;
                packetData[i] = new byte[0];
                toRemove.add(i);
            }
        }

        // Remove invalid packets
        if (!toRemove.isEmpty()) {
            int newCount = packetCount - toRemove.size();
            int[] newIds = new int[newCount];
            long[] newTimestamps = new long[newCount];
            byte[][] newData = new byte[newCount][];

            int index = 0;
            for (int i = 0; i < packetCount; i++) {
                if (!toRemove.contains(i)) {
                    newIds[index] = packetIds[i];
                    newTimestamps[index] = packetTimestamps[i];
                    newData[index] = packetData[i];
                    index++;
                }
            }

            this.packetCount = newCount;
            this.packetIds = newIds;
            this.packetTimestamps = newTimestamps;
            this.packetData = newData;
        }

    }

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeVarInt(packetCount);
        for (int i = 0; i < packetCount; i++) {
            buffer.writeVarInt(packetIds[i]);
            buffer.writeVarLong(packetTimestamps[i]);
            buffer.writeByteArray(packetData[i]);
        }
    }

    public Batch() {

    }

    @Override
    public void read(final PacketBuffer buffer) {
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

    public Packet[] decode(final TriFunction<Packet, Integer, Long, byte[]> constructor) {
        Packet[] packets = new Packet[packetCount];
        for (int i = 0; i < packetCount; i++) {
            packets[i] = constructor.apply(packetIds[i], packetTimestamps[i], packetData[i]);
        }
        return packets;
    }

}
