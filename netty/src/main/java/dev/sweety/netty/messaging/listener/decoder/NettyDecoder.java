package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.core.logger.LogLevel;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.messaging.exception.PacketDecodeException;
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

    public NettyDecoder(IPacketRegistry packetRegistry) {
        packetDecoder = new PacketDecoder(packetRegistry);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        List<Packet> packets = new ArrayList<>(1);
        try {
            PacketBuffer buf = new PacketBuffer(in).retain();
            packetDecoder.decode(buf, packets);
            buf.release();
        } catch (PacketDecodeException e) {
            SimpleLogger.log(LogLevel.DEBUG, "exception from context: " + ctx.channel().remoteAddress());
            throw new RuntimeException(e);
        }
        out.addAll(packets);
    }
}
