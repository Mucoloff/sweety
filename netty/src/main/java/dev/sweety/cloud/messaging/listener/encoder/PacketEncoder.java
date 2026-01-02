package dev.sweety.cloud.messaging.listener.encoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.cloud.messaging.exception.PacketEncodeException;
import dev.sweety.cloud.messaging.model.Messenger;
import dev.sweety.cloud.packet.model.Packet;
import dev.sweety.cloud.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;

public class PacketEncoder {
    static final int ZIP_THRESHOLD = 256;
    final IPacketRegistry packetRegistry;

    public PacketEncoder(final IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    protected void encode(Packet packet, ByteBuf out) throws Exception {
        short packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId < 0)
            throw new PacketEncodeException("Returned PacketId by registry is < 0");

        out.writeShort(packetId);
        boolean hasTimestamp = packet.timestamp() <= 0L;
        out.writeBoolean(hasTimestamp);

        if (hasTimestamp) out.writeLong(packet.timestamp());


        byte[] bufferData = packet.buffer().getBytes();

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
        out.writeInt(ChecksumUtils.crc32Int(data, Messenger.SEED));
        out.writeInt(data.length);
        out.writeBytes(data);
    }
}