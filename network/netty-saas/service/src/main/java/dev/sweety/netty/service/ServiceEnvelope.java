package dev.sweety.netty.service;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.netty.packet.registry.PacketRegistry;

/**
 * Routing envelope carried inside a {@link ServiceMessage}: {@code senderId}/{@code receiverId} are
 * numeric service ids the {@link HubServer} routes on, and the inner application packet travels as an
 * opaque {@code (innerId, innerData)} pair so the hub can relay it without decoding. An {@link #INNER_NONE}
 * inner id (empty data) is an empty ack — {@link #decode(PacketRegistry)} returns {@code null} for it.
 */
public final class ServiceEnvelope extends PacketTransaction.Transaction {

    /** Inner id marking an empty payload (e.g. a bare RPC ack). */
    public static final int INNER_NONE = -1;
    private static final byte[] EMPTY = new byte[0];

    private int senderId;
    private int receiverId;
    private int innerId = INNER_NONE;
    private byte[] innerData = EMPTY;
    /** Set by {@link HubServer} when it synthesizes this envelope as an immediate no-route nack. */
    private boolean noRoute;

    public ServiceEnvelope() {}

    private ServiceEnvelope(int senderId, int receiverId, int innerId, byte[] innerData) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.innerId = innerId;
        this.innerData = innerData;
    }

    /** Wraps {@code inner} (nullable → empty ack) addressed from {@code senderId} to {@code receiverId}. */
    public static ServiceEnvelope wrap(int senderId, int receiverId, Packet inner, PacketRegistry registry) {
        if (inner == null) return new ServiceEnvelope(senderId, receiverId, INNER_NONE, EMPTY);
        int id = registry.getPacketId(inner.getClass());
        if (id < 0) throw new IllegalArgumentException("Packet not registered: " + inner.getClass().getName());
        return new ServiceEnvelope(senderId, receiverId, id, serialize(inner));
    }

    /**
     * Synthesized by {@link HubServer#route} in place of a relayed reply when the target service isn't
     * connected — lets the RPC caller's future fail immediately instead of riding out its full timeout.
     */
    public static ServiceEnvelope noRoute(int senderId, int receiverId) {
        ServiceEnvelope envelope = new ServiceEnvelope(senderId, receiverId, INNER_NONE, EMPTY);
        envelope.noRoute = true;
        return envelope;
    }

    public boolean isNoRoute() { return noRoute; }

    /** Decodes the inner packet via the registry, or {@code null} for an empty ack. */
    public Packet decode(PacketRegistry registry) {
        if (innerId < 0) return null;
        try {
            return registry.constructPacket(innerId, -1L, innerData);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to decode inner packet id " + innerId, e);
        }
    }

    private static byte[] serialize(Packet packet) {
        PacketBuffer buffer = new PacketBuffer();
        try {
            packet.write(buffer);
            byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out;
        } finally {
            buffer.release();
        }
    }

    public int senderId() { return senderId; }
    public int receiverId() { return receiverId; }

    /**
     * Overrides the sender id with the identity {@link HubServer} actually verified for the originating
     * channel at identify time, discarding whatever the wire payload itself claimed. Package-private:
     * only the hub is trusted to call this, and only right after receiving the envelope, before it's
     * forwarded to the target — otherwise a sender could forge {@code senderId} to impersonate any other
     * mesh participant and reach a callee that trusts its caller's declared identity instead of
     * re-deriving it (several mesh edges do exactly that, by design, since {@link HubServer} is meant
     * to be the one place that verifies who's actually on the other end of a channel).
     */
    void overrideSenderId(int verifiedSenderId) {
        this.senderId = verifiedSenderId;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeVarInt(senderId);
        buffer.writeVarInt(receiverId);
        buffer.writeVarInt(innerId);
        buffer.writeVarInt(innerData.length);
        buffer.writeBytes(innerData);
        buffer.writeBoolean(noRoute);
    }

    @Override
    public void read(BufferReader buffer) {
        senderId = buffer.readVarInt();
        receiverId = buffer.readVarInt();
        innerId = buffer.readVarInt();
        int len = buffer.readVarInt();
        innerData = new byte[len];
        buffer.readBytes(innerData);
        noRoute = buffer.readBoolean();
    }
}
