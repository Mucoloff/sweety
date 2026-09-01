package dev.sweety.netty.packet.internal;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.packet.Packer;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.function.Function;

public class ForwardData extends PacketTransaction.Transaction {

    private int senderId, receiverId;
    private RoutingContext context;
    private Batch batch;

    /**
     * Constructor for creating a new Forward with packets.
     */
    public ForwardData(final int senderId, final int receiverId, final RoutingContext context,
                       final Function<Class<? extends Packet>, Integer> idMap,
                       final Packet... packets) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.context = context != null ? context : RoutingContext.empty();
        this.batch = new Batch(idMap, p -> p instanceof InternalPacket, packets);
    }

    /**
     * Legacy constructor without RoutingContext (defaults to empty context).
     */
    public ForwardData(final int senderId, final int receiverId,
                       final Function<Class<? extends Packet>, Integer> idMap,
                       final Packet... packets) {
        this(senderId, receiverId, null, idMap, packets);
    }

    /**
     * Empty constructor for deserialization.
     */
    public ForwardData() {
        this.context = RoutingContext.empty();
        this.batch = new Batch();
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeVarInt(this.senderId);
        buffer.writeVarInt(this.receiverId);
        (this.context != null ? this.context : RoutingContext.empty()).write(buffer);
        // rawBatchBytes() returns the pre-serialized form, skipping intermediate PacketBuffer allocation
        buffer.writeByteArray(this.batch.rawBatchBytes());
    }

    @Override
    public void read(final BufferReader buffer) {
        this.senderId = buffer.readVarInt();
        this.receiverId = buffer.readVarInt();
        this.context = RoutingContext.readContext(buffer);
        this.batch = new Batch();
        final PacketBuffer bytes = new PacketBuffer(buffer.readByteArray());
        try {
            this.batch.read(bytes);
        } finally {
            bytes.release();
        }
    }

    /**
     * Decode the batch payload. This is lazy-loaded on first call.
     */
    public Packet[] decode(final Batch.Constructor constructor) {
        return this.batch != null ? this.batch.decode(constructor) : Packer.EMPTY();
    }

    /**
     * Get the routing context (no deserialization needed).
     */
    public RoutingContext context() {
        return this.context;
    }

    public int senderId() {
        return this.senderId;
    }

    public int receiverId() {
        return this.receiverId;
    }

    public Batch batch() {
        return this.batch;
    }

    public byte[] rawBatchBytes() {
        return this.batch != null ? this.batch.rawBatchBytes() : new byte[0];
    }

    public boolean isDecoded() {
        return this.batch != null && this.batch.isDecoded();
    }

    public void release() {
        if (this.context != null) {
            this.context.release();
        }
    }
}
