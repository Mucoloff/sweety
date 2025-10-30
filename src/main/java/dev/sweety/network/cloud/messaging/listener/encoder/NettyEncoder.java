package dev.sweety.network.cloud.messaging.listener.encoder;

import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyEncoder extends MessageToByteEncoder<PacketOut> {
    public NettyEncoder() {
    }

    protected void encode(ChannelHandlerContext ctx, PacketOut packet, ByteBuf out) {
        out.writeByte(packet.getId());
        boolean hasTimestamp = packet.getTimestamp() != null;
        out.writeBoolean(hasTimestamp);

        if (hasTimestamp) out.writeLong(packet.getTimestamp());

        byte[] data = packet.getData();
        out.writeInt(data.length);
        out.writeBytes(data);
    }
}
