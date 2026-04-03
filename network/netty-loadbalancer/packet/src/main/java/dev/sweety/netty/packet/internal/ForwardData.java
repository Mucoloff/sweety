package dev.sweety.netty.packet.internal;

import dev.sweety.math.function.TriFunction;
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
        this.context = context != null ? context : new RoutingContext();
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
        this.context = new RoutingContext();
        this.batch = new Batch();
    }

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeVarInt(this.senderId);
        buffer.writeVarInt(this.receiverId);
        this.context.write(buffer);
        final PacketBuffer payload = new PacketBuffer();
        try {
            this.batch.write(payload);
            buffer.writeByteArray(payload.getBytes());
        } finally {
            payload.release();
        }
    }

    @Override
    public void read(final PacketBuffer buffer) {
        this.senderId = buffer.readVarInt();
        this.receiverId = buffer.readVarInt();
        this.context = new RoutingContext();
        this.context.read(buffer);
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
    public Packet[] decode(final TriFunction<Packet, Integer, Long, byte[]> constructor) {
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
}
