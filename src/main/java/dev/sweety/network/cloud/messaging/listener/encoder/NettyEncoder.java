package dev.sweety.network.cloud.messaging.listener.encoder;

import dev.sweety.core.util.ResourceUtils;
import dev.sweety.network.cloud.messaging.exception.PacketEncodeException;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;


public class NettyEncoder extends MessageToByteEncoder<Packet> {
    private static final int ZIP_THRESHOLD = 256;

    private final IPacketRegistry packetRegistry;

    public NettyEncoder(IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) {
        byte packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId < 0)
            throw new PacketEncodeException("Returned PacketId by registry is < 0");

        out.writeByte(packetId);
        boolean hasTimestamp = packet.getTimestamp() <= 0L;
        out.writeBoolean(hasTimestamp);

        if (hasTimestamp) out.writeLong(packet.getTimestamp());


        byte[] bufferData = packet.getData();

        byte[] data = bufferData != null ? bufferData : new byte[0];
        boolean compressed = false;


        if (data.length >= ZIP_THRESHOLD) {
            byte[] zipped = ResourceUtils.zipBytes(data, "zipped-buffer");
            if (zipped.length < data.length) {
                data = zipped;
                compressed = true;
            }
        }


        out.writeBoolean(compressed);
        out.writeInt(data.length);
        out.writeBytes(data);
    }
}
