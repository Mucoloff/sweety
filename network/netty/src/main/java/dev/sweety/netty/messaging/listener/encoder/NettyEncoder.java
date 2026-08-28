package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyEncoder extends MessageToByteEncoder<Packet> {

    /** Thread-local wrapper reused across encode calls — avoids one allocation per packet sent. */
    private static final ThreadLocal<PacketBuffer> ENCODE_WRAPPER =
            ThreadLocal.withInitial(PacketBuffer::wrapper);

    private final PacketEncoder packetEncoder;

    public NettyEncoder(PacketRegistry packetRegistry) {
        packetEncoder = new PacketEncoder(packetRegistry);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception {
        // Wrap the netty-owned output buffer; netty manages its lifecycle, no retain/release here.
        packetEncoder.encode(packet, ENCODE_WRAPPER.get().wrapExternal(out));
    }
}
