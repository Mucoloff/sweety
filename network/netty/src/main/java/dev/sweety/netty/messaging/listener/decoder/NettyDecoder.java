package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.logger.LogLevel;
import dev.sweety.logger.SimpleLogger;
import dev.sweety.netty.messaging.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.ArrayList;
import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {

    private final PacketDecoder packetDecoder;
    private final Messenger<?> messenger;

    public NettyDecoder(IPacketRegistry packetRegistry) {
        this(packetRegistry, null);
    }

    public NettyDecoder(IPacketRegistry packetRegistry, Messenger<?> messenger) {
        this.packetDecoder = new PacketDecoder(packetRegistry);
        this.messenger = messenger;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        final List<Packet> packets = new ArrayList<>(1);
        try {
            PacketBuffer buf = new PacketBuffer(in).retain();
            this.packetDecoder.decode(buf, packets);
            buf.release();
        } catch (PacketDecodeException e) {
            SimpleLogger.log(LogLevel.WARN, "netty-decoder",
                    "decode exception from " + ctx.channel().remoteAddress() + " -> ", e);
            throw new RuntimeException(e);
        }
        if (!packets.isEmpty()) {
            for (Packet packet : packets) {
                if (packet == null) continue;
                SimpleLogger.log(LogLevel.INFO, "netty-decoder",
                        "decoded " + packet.name() + "(" + packet.id() + ") from " + ctx.channel().remoteAddress());
            }
        }
        // Fast-path dispatch: deliver packets directly to messenger.
        // This avoids dependency on downstream channelRead propagation in watcher.
        if (this.messenger != null && !packets.isEmpty()) {
            for (Packet packet : packets) {
                if (packet == null) continue;
                try {
                    this.messenger.onPacketReceive(ctx, packet);
                } finally {
                    packet.release();
                }
            }
            return;
        }
        out.addAll(packets);
    }
}