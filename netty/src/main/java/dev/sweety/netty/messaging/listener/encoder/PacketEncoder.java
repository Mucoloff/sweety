package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketEncodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class PacketEncoder {
    static final int ZIP_THRESHOLD = 256;
    final IPacketRegistry packetRegistry;

    public PacketEncoder(final IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    public void encode(Packet packet, ByteBuf out) throws PacketEncodeException {
        short packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId < 0)
            throw new PacketEncodeException("Returned PacketId by registry is < 0");

        out.writeShort(packetId);
        // hasTimestamp should be true when timestamp is actually present (> 0)
        boolean hasTimestamp = packet.timestamp() > 0L;
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

        CRC32 crc32 = ChecksumUtils.crc32(true);
        crc32.update(ByteBuffer.allocate(4).putInt(Messenger.SEED).array());
        crc32.update(data);
        long check = crc32.getValue();

        out.writeBoolean(compressed);
        out.writeLong(check);
        out.writeInt(data.length);
        out.writeBytes(data);
    }
}