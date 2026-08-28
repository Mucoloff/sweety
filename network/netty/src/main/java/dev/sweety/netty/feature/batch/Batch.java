package dev.sweety.netty.feature.batch;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.math.function.TriFunction;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.netty.packet.buffer.io.Codec;
import dev.sweety.netty.packet.model.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Batch implements Codec {

    // Sane upper bound on packets per batch — guards against a bogus/huge declared packetCount
    // driving an unbounded int[]/long[]/Packet[] allocation before any real data is validated
    // (same class of issue as the array-length guards elsewhere in AbstractBuffer).
    private static final int MAX_BATCH_PACKETS = 1 << 16;

    private int packetCount;
    private int[] packetIds;
    private long[] packetTimestamps;
    private Packet[] packets;

    private byte[] rawBatchBytes;
    private boolean metadataDecoded;

    public Batch(final Function<Class<? extends Packet>, Integer> idMap,
                 final Predicate<Packet> exclusion,
                 final Packet... packets) {
        this();

        if (packets == null || packets.length == 0) {
            this.packetCount = 0;
            this.packetIds = new int[0];
            this.packetTimestamps = new long[0];
            this.packets = new Packet[0];
            this.metadataDecoded = true;
            return;
        }

        final int[] tempIds = new int[packets.length];
        final long[] tempTimestamps = new long[packets.length];
        final List<Packet> validPackets = new ArrayList<>(packets.length);
        int validCount = 0;

        for (final Packet packet : packets) {
            if (packet == null || exclusion.test(packet)) continue;
            final Integer mappedId = idMap.apply(packet.getClass());
            if (mappedId == null || mappedId < 0) continue;
            tempIds[validCount++] = mappedId;
            tempTimestamps[validCount - 1] = packet.timestamp();
            validPackets.add(packet);
        }

        this.packetCount = validCount;
        this.packetIds = new int[validCount];
        this.packetTimestamps = new long[validCount];
        this.packets = validPackets.toArray(Packet[]::new);

        System.arraycopy(tempIds, 0, this.packetIds, 0, validCount);
        System.arraycopy(tempTimestamps, 0, this.packetTimestamps, 0, validCount);

        this.metadataDecoded = true;
    }

    public Batch() {
        this.packetCount = 0;
        this.packetIds = new int[0];
        this.packetTimestamps = new long[0];
        this.rawBatchBytes = null;
        this.packets = null;
        this.metadataDecoded = false;
    }

    @Override
    public void write(final BufferWriter buffer) {
        final byte[] payload = ensureRawPayload();
        if (buffer instanceof PacketBuffer packetBuffer) {
            packetBuffer.writeBytes(payload);
            return;
        }
        buffer.writeBytes(payload);
    }

    @Override
    public void read(final BufferReader buffer) {
        this.rawBatchBytes = buffer.getBytes();
        buffer.readerIndex(buffer.writerIndex());
        this.packets = null;
        this.metadataDecoded = false;
    }

    public Packet[] decode(final Constructor constructor) {
        if (this.packets != null) {
            return this.packets;
        }

        if (this.rawBatchBytes == null) {
            this.packetCount = 0;
            this.packetIds = new int[0];
            this.packetTimestamps = new long[0];
            this.packets = new Packet[0];
            this.metadataDecoded = true;
            return this.packets;
        }

        ensureMetadata();

        final Packet[] packets = new Packet[this.packetCount];
        try (PacketBuffer payload = new PacketBuffer(this.rawBatchBytes)) {
            payload.readVarInt();
            long prevTimestamp = 0L;
            for (int i = 0; i < this.packetCount; i++) {
                final int packetId = payload.readVarInt();
                final long timestamp = i == 0 ? payload.readVarLong() : prevTimestamp + payload.readVarLongZigZag();
                prevTimestamp = timestamp;
                final int payloadLength = payload.readVarInt();
                try (PacketBuffer packetPayload = payload.readRetainedSlice(payloadLength)) {
                    packets[i] = constructor.apply(packetId, timestamp, packetPayload);
                }
            }
        }

        this.packets = packets;
        return packets;
    }

    public int packetCount() {
        ensureMetadata();
        return this.packetCount;
    }

    public int[] packetIds() {
        ensureMetadata();
        return this.packetIds;
    }

    public long[] packetTimestamps() {
        ensureMetadata();
        return this.packetTimestamps;
    }

    public byte[] rawBatchBytes() {
        ensureRawPayload();
        return this.rawBatchBytes;
    }

    public boolean isDecoded() {
        return this.packets != null;
    }

    private byte[] ensureRawPayload() {
        if (this.rawBatchBytes == null) {
            this.rawBatchBytes = serializePayload();
        }
        return this.rawBatchBytes;
    }

    private void ensureMetadata() {
        if (this.metadataDecoded) {
            return;
        }

        if (this.rawBatchBytes == null) {
            this.packetCount = 0;
            this.packetIds = new int[0];
            this.packetTimestamps = new long[0];
            this.metadataDecoded = true;
            return;
        }

        try (PacketBuffer payload = new PacketBuffer(this.rawBatchBytes)) {
            this.packetCount = payload.readVarInt();
            if (this.packetCount < 0 || this.packetCount > MAX_BATCH_PACKETS)
                throw PacketDecodeException.of("Batch packetCount out of bounds: " + this.packetCount).runtime();
            this.packetIds = new int[this.packetCount];
            this.packetTimestamps = new long[this.packetCount];

            long prevTimestamp = 0L;
            for (int i = 0; i < this.packetCount; i++) {
                this.packetIds[i] = payload.readVarInt();
                prevTimestamp = i == 0 ? payload.readVarLong() : prevTimestamp + payload.readVarLongZigZag();
                this.packetTimestamps[i] = prevTimestamp;
                final int packetPayloadLength = payload.readVarInt();
                payload.readerIndex(payload.readerIndex() + packetPayloadLength);
            }
        }

        this.metadataDecoded = true;
    }

    /**
     * Convenience entry point that uses the same lazy batch codec path as {@link #write(BufferWriter)}.
     */
    public static void writeDirect(
            final BufferWriter target,
            final Function<Class<? extends Packet>, Integer> idMap,
            final Predicate<Packet> exclusion,
            final Packet... packets) {
        new Batch(idMap, exclusion, packets).write(target);
    }

    private byte[] serializePayload() {
        try (PacketBuffer payload = PacketBufferAllocator.DEFAULT.buffer();
             PacketBuffer packetPayload = PacketBufferAllocator.DEFAULT.buffer()) {
            payload.writeVarInt(this.packetCount);

            if (this.packets == null || this.packetCount == 0) {
                return payload.getBytes();
            }

            long prevTimestamp = 0L;
            for (int i = 0; i < this.packetCount; i++) {
                appendPacket(payload, packetPayload, this.packetIds[i], this.packetTimestamps[i], prevTimestamp, i == 0, this.packets[i]);
                prevTimestamp = this.packetTimestamps[i];
            }
            return payload.getBytes();
        }
    }

    private static void appendPacket(final PacketBuffer payload,
                                     final PacketBuffer packetPayload,
                                     final int packetId,
                                     final long timestamp,
                                     final long prevTimestamp,
                                     final boolean first,
                                     final Packet packet) {
        packetPayload.clear();
        packet.write(packetPayload);

        payload.writeVarInt(packetId);
        // First entry carries the absolute timestamp; subsequent entries delta+zigzag against the
        // previous packet in this same batch — timestamps within a batch are almost always close
        // together, so the delta collapses to 1 byte instead of the full varlong.
        if (first) payload.writeVarLong(timestamp);
        else payload.writeVarLongZigZag(timestamp - prevTimestamp);
        payload.writeVarInt(packetPayload.readableBytes());
        payload.writeBuffer(packetPayload);
    }

    public interface Constructor extends TriFunction<Packet, Integer, Long, BufferReader> {

        Packet construct(int id, long timestamp, BufferReader data);

        @Override
        default Packet apply(Integer id, Long timestamp, BufferReader data) {
            return this.construct(id, timestamp, data);
        }

    }
}