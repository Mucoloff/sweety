package dev.sweety.network.cloud.messaging.listener.decoder;

import dev.sweety.network.cloud.packet.incoming.PacketIn;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {
    public NettyDecoder() {
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) return;

        in.markReaderIndex();
        byte id = in.readByte();
        boolean hasTimestamp = in.readBoolean();
        long timestamp;
        if (hasTimestamp) {
            if (in.readableBytes() < 8) {
                in.resetReaderIndex();
                return;
            }

            timestamp = in.readLong();
        } else timestamp = System.currentTimeMillis();

        if (in.readableBytes() < 2) {
            in.resetReaderIndex();
            return;
        }
        int length = in.readInt();
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }
        byte[] data = new byte[length];
        in.readBytes(data);
        out.add(new PacketIn(id, timestamp, data));
    }
}
