package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.ArrayList;
import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {

    private static final SimpleLogger LOGGER = SimpleLogger.of("netty-decoder");

    /** Thread-local wrapper reused across decode calls — avoids one allocation per packet received. */
    private static final ThreadLocal<PacketBuffer> DECODE_WRAPPER =
            ThreadLocal.withInitial(PacketBuffer::wrapper);

    /** Thread-local list reused across decode calls — avoids one ArrayList alloc per packet received. */
    private static final ThreadLocal<ArrayList<Packet>> DECODE_LIST =
            ThreadLocal.withInitial(() -> new ArrayList<>(4));

    private final PacketDecoder packetDecoder;
    private final Messenger messenger;

    public NettyDecoder(PacketRegistry packetRegistry) {
        this(packetRegistry, null);
    }

    public NettyDecoder(PacketRegistry packetRegistry, Messenger messenger) {
        this.packetDecoder = new PacketDecoder(packetRegistry);
        this.messenger = messenger;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        final ArrayList<Packet> packets = DECODE_LIST.get();
        packets.clear();
        try {
            this.packetDecoder.decode(DECODE_WRAPPER.get().wrapExternal(in), packets);
        } catch (PacketDecodeException e) {
            LOGGER.warn("decode exception from {} ->", ctx.channel().remoteAddress(), e);
            throw new RuntimeException(e);
        }
        if (packets.isEmpty()) return;
        // Fast-path: deliver directly to messenger (avoids downstream channelRead propagation).
        if (this.messenger != null) {
            for (int i = 0, n = packets.size(); i < n; i++) {
                Packet packet = packets.get(i);
                if (packet == null) continue;
                this.messenger.onPacketReceive(ctx, packet);
            }
            return;
        }
        out.addAll(packets);
    }
}