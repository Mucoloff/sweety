package dev.sweety.netty.loadbalancer.common.packet.internal;

import dev.sweety.core.math.function.TriFunction;
import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.loadbalancer.common.packet.Packer;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.record.annotations.RecordGetter;

import java.util.function.Function;

public class ForwardData extends PacketTransaction.Transaction {

    // --- HEADER (always read) ---
    @RecordGetter
    private int senderId, receiverId;
    private RoutingContext context;

    // --- PAYLOAD (lazy) ---
    private byte[] rawBatchBytes;
    private Batch batch;
    private boolean decoded = false;

    /**
     * Constructor for creating a new Forward with packets.
     */
    public ForwardData(final int senderId, final int receiverId, final RoutingContext context,
                       Function<Class<? extends Packet>, Integer> idMap, Packet... packets) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.context = context != null ? context : new RoutingContext();
        this.batch = new Batch(idMap, p -> p instanceof InternalPacket, packets);
        this.decoded = true;
        // Pre-serialize batch for rawBatchBytes
        final PacketBuffer tempBuffer = new PacketBuffer();
        this.batch.write(tempBuffer);
        this.rawBatchBytes = tempBuffer.getBytes();
    }

    /**
     * Legacy constructor without RoutingContext (defaults to empty context).
     */
    public ForwardData(final int senderId, final int receiverId,
                       Function<Class<? extends Packet>, Integer> idMap, Packet... packets) {
        this(senderId, receiverId, null, idMap, packets);
    }

    /**
     * Empty constructor for deserialization.
     */
    public ForwardData() {
        this.context = new RoutingContext();
    }

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeVarInt(this.senderId);
        buffer.writeVarInt(this.receiverId);
        this.context.write(buffer);
        buffer.writeByteArray(this.rawBatchBytes);
    }

    @Override
    public void read(final PacketBuffer buffer) {
        this.senderId = buffer.readVarInt();
        this.receiverId = buffer.readVarInt();
        this.context = new RoutingContext();
        this.context.read(buffer);
        // LAZY: store raw bytes, don't decode yet
        this.rawBatchBytes = buffer.readByteArray();
        this.decoded = false;
    }

    /**
     * Decode the batch payload. This is lazy-loaded on first call.
     */
    public Packet[] decode(TriFunction<Packet, Integer, Long, byte[]> constructor) {
        if (!decoded && rawBatchBytes != null) {
            this.batch = new Batch();
            this.batch.read(new PacketBuffer(rawBatchBytes));
            this.decoded = true;
        }
        return this.batch != null ? this.batch.decode(constructor) : Packer.EMPTY;
    }

    /**
     * Get the routing context (no deserialization needed).
     */
    public RoutingContext context() {
        return this.context;
    }

}
