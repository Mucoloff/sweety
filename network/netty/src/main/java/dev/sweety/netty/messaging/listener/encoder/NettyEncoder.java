package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.netty.messaging.listener.PacketCodecSupport;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * TCP stream packet encoder. Delegates to {@link PacketCodecSupport} for zero-allocation buffer wrapping.
 */
public class NettyEncoder extends MessageToByteEncoder<Packet> {

    private final PacketEncoder packetEncoder;

    public NettyEncoder(PacketRegistry packetRegistry) {
        this.packetEncoder = new PacketEncoder(packetRegistry);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception {
        PacketCodecSupport.encodeStream(this.packetEncoder, packet, out);
    }
}
