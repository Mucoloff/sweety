package dev.sweety.network.cloud.messaging.listener.decoder;

import dev.sweety.core.util.ResourceUtils;
import dev.sweety.network.cloud.messaging.exception.PacketDecodeException;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {

    private final IPacketRegistry packetRegistry;
    public NettyDecoder(IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)  {
        // minimum possible: 1 (id) + 1 (boolean ts) + 1 (boolean compressed) + 4 (data length) = 7
        if (in.readableBytes() < 7) return; // buffer incomplete: wait for more data

        in.markReaderIndex();
        byte id = in.readByte();
        boolean hasTimestamp = in.readBoolean();
        long timestamp;
        if (hasTimestamp) {
            // need 8 bytes for timestamp
            if (in.readableBytes() < 8) {
                in.resetReaderIndex();
                return; // incomplete timestamp: wait for more data
            }

            timestamp = in.readLong();
        } else timestamp = System.currentTimeMillis();

        // read compressed flag
        if (in.readableBytes() < 1) {
            in.resetReaderIndex();
            return; // incomplete: wait for more data
        }
        boolean compressed = in.readBoolean();

        // need 4 bytes for the data length int
        if (in.readableBytes() < 4) {
            in.resetReaderIndex();
            return; // incomplete: wait for more data
        }
        int length = in.readInt();

        // validate length
        if (length < 0) {
            // invalid or too large - close the connection to avoid abuse
            ctx.close();
            throw new PacketDecodeException("Invalid packet length: " + length + ", closing connection.");
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return; // incomplete payload: wait for more data
        }
        byte[] data = new byte[length];
        in.readBytes(data);

        byte[] bytes = compressed ? ResourceUtils.unzipBytes(data) : data;

        final Packet packet;

        try {
            packet = packetRegistry.constructPacket(id, timestamp, bytes);
        } catch (Exception e) {
            throw new PacketDecodeException("Failed to decode packet ("+id+")", e);
        }

        if (compressed) System.out.println("unzipped packet with size " + packet.getBuffer().readableBytes());
        out.add(packet);
    }
}
