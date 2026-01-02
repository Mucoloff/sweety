package dev.sweety.cloud.messaging.listener.decoder;

import dev.sweety.cloud.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {

    private final PacketDecoder packetDecoder;

    public NettyDecoder(IPacketRegistry packetRegistry) {
        packetDecoder = new PacketDecoder(packetRegistry);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        packetDecoder.decode(in, out);
    }
}
