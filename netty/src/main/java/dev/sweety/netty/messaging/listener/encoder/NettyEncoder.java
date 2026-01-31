package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.netty.messaging.exception.PacketEncodeException;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyEncoder extends MessageToByteEncoder<Packet> {

    private final PacketEncoder packetEncoder;

    public NettyEncoder(IPacketRegistry packetRegistry) {
        packetEncoder = new PacketEncoder(packetRegistry);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception {
        PacketBuffer buffer = new PacketBuffer(out).retain();
        try {
            packetEncoder.encode(packet, buffer);
        } finally {
            buffer.release();
        }
    }
}
