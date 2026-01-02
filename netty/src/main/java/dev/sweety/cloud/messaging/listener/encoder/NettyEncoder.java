package dev.sweety.cloud.messaging.listener.encoder;

import dev.sweety.cloud.packet.model.Packet;
import dev.sweety.cloud.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;


public class NettyEncoder extends MessageToByteEncoder<Packet> {

    private final PacketEncoder packetEncoder;

    public NettyEncoder(IPacketRegistry packetRegistry) {
        packetEncoder = new PacketEncoder(packetRegistry);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception{
        packetEncoder.encode(packet, out);
    }
}
