package dev.sweety.netty.packet.queue;

import dev.sweety.math.pool.ObjectPool;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;

/**
 * Contesto di un pacchetto ricevuto, con il sequence ID per mantenere l'ordine.
 */
public final class PacketContext {

    public static final ObjectPool<PacketContext> POOL = ObjectPool.threadLocal(PacketContext::new)
            .reset(PacketContext::reset)
            .build();

    private Packet packet;
    private ChannelHandlerContext ctx;
    private long sequenceId;

    public PacketContext() {
        this(null, null, -1L);
    }

    public PacketContext(Packet packet, ChannelHandlerContext ctx) {
        this(packet, ctx, -1L);
    }

    public PacketContext(Packet packet, ChannelHandlerContext ctx, long sequenceId) {
        this.packet = packet;
        this.ctx = ctx;
        this.sequenceId = sequenceId;
    }

    public static PacketContext of(Packet packet, ChannelHandlerContext ctx, long sequenceId) {
        PacketContext c = POOL.acquire();
        c.packet = packet;
        c.ctx = ctx;
        c.sequenceId = sequenceId;
        return c;
    }

    public void release() {
        POOL.release(this);
    }

    public void reset() {
        this.packet = null;
        this.ctx = null;
        this.sequenceId = -1L;
    }

    public Packet packet() {
        return packet;
    }

    public ChannelHandlerContext ctx() {
        return ctx;
    }

    public long sequenceId() {
        return sequenceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PacketContext that)) return false;
        return sequenceId == that.sequenceId && Objects.equals(packet, that.packet) && Objects.equals(ctx, that.ctx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packet, ctx, sequenceId);
    }

    @Override
    public String toString() {
        return "PacketContext[packet=" + packet + ", ctx=" + ctx + ", sequenceId=" + sequenceId + "]";
    }
}