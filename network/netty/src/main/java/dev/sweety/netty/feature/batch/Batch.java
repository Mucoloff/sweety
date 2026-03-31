package dev.sweety.netty.feature.batch;

import dev.sweety.math.function.TriFunction;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Codec;
import dev.sweety.netty.packet.model.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Batch implements Codec {

    private int packetCount;
    private int[] packetIds;
    private long[] packetTimestamps;
    private byte[][] packetData;

    private byte[] rawBatchBytes;
    private boolean decoded;

    public Batch(final Function<Class<? extends Packet>, Integer> idMap,
                 final Predicate<Packet> exclusion,
                 final Packet... packets) {
        this();

        if (packets == null || packets.length == 0) {
            this.decoded = true;
            this.rawBatchBytes = serializePayload();
            return;
        }

        final List<Packet> validPackets = new ArrayList<>(packets.length);
        for (final Packet packet : packets) {
            if (packet == null || exclusion.test(packet)) {
                continue;
            }

            final Integer mappedId = idMap.apply(packet.getClass());
            if (mappedId == null || mappedId < 0) {
                continue;
            }

            validPackets.add(packet);
        }

        this.packetCount = validPackets.size();
        this.packetIds = new int[this.packetCount];
        this.packetTimestamps = new long[this.packetCount];
        this.packetData = new byte[this.packetCount][];

        for (int i = 0; i < this.packetCount; i++) {
            final Packet packet = validPackets.get(i);
            this.packetIds[i] = idMap.apply(packet.getClass());
            this.packetTimestamps[i] = packet.timestamp();
            this.packetData[i] = packet.buffer().getBytes();
        }

        this.decoded = true;
        this.rawBatchBytes = serializePayload();
    }

    public Batch() {
        this.packetCount = 0;
        this.packetIds = new int[0];
        this.packetTimestamps = new long[0];
        this.packetData = new byte[0][];
        this.rawBatchBytes = null;
        this.decoded = false;
    }

    @Override
    public void write(final PacketBuffer buffer) {
        ensureRawPayload();
        buffer.writeBytes(this.rawBatchBytes);
    }

    @Override
    public void read(final PacketBuffer buffer) {
        // Keep the payload raw and parse it only when decode() is requested.
        this.rawBatchBytes = buffer.getBytes();
        buffer.readerIndex(buffer.writerIndex());
        this.decoded = false;
    }

    public Packet[] decode(final TriFunction<Packet, Integer, Long, byte[]> constructor) {
        ensureDecoded();

        final Packet[] packets = new Packet[this.packetCount];
        for (int i = 0; i < this.packetCount; i++) {
            packets[i] = constructor.apply(this.packetIds[i], this.packetTimestamps[i], this.packetData[i]);
        }
        return packets;
    }

    public int packetCount() {
        ensureDecoded();
        return this.packetCount;
    }

    public int[] packetIds() {
        ensureDecoded();
        return this.packetIds;
    }

    public long[] packetTimestamps() {
        ensureDecoded();
        return this.packetTimestamps;
    }

    public byte[][] packetData() {
        ensureDecoded();
        return this.packetData;
    }

    public byte[] rawBatchBytes() {
        ensureRawPayload();
        return this.rawBatchBytes;
    }

    public boolean isDecoded() {
        return this.decoded;
    }

    private void ensureRawPayload() {
        if (this.rawBatchBytes == null) {
            this.rawBatchBytes = serializePayload();
        }
    }

    private void ensureDecoded() {
        if (this.decoded) {
            return;
        }

        if (this.rawBatchBytes == null) {
            this.packetCount = 0;
            this.packetIds = new int[0];
            this.packetTimestamps = new long[0];
            this.packetData = new byte[0][];
            this.decoded = true;
            return;
        }

        final PacketBuffer payload = new PacketBuffer(this.rawBatchBytes);
        this.packetCount = payload.readVarInt();
        this.packetIds = new int[this.packetCount];
        this.packetTimestamps = new long[this.packetCount];
        this.packetData = new byte[this.packetCount][];

        for (int i = 0; i < this.packetCount; i++) {
            this.packetIds[i] = payload.readVarInt();
            this.packetTimestamps[i] = payload.readVarLong();
            this.packetData[i] = payload.readByteArray();
        }

        this.decoded = true;
    }

    private byte[] serializePayload() {
        final PacketBuffer payload = new PacketBuffer();
        payload.writeVarInt(this.packetCount);

        for (int i = 0; i < this.packetCount; i++) {
            payload.writeVarInt(this.packetIds[i]);
            payload.writeVarLong(this.packetTimestamps[i]);
            payload.writeByteArray(this.packetData[i]);
        }

        return payload.getBytes();
    }
}