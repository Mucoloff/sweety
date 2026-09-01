package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.netty.messaging.listener.PacketCodecSupport;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * TCP stream packet decoder. Uses {@link PacketCodecSupport} for zero-copy buffer wrapping and fast-path dispatch.
 */
public class NettyDecoder extends ByteToMessageDecoder {

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
        final ArrayList<Packet> packets = PacketCodecSupport.decodePackets(this.packetDecoder, in, ctx.channel().remoteAddress());
        PacketCodecSupport.dispatch(ctx, packets, this.messenger, out);
    }
}
