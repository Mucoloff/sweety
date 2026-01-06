package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketEncodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
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
        int packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId < 0)
            throw new PacketEncodeException("Returned PacketId by registry is < 0");

        final PacketBuffer buf = new PacketBuffer();

        final boolean hasTimestamp = packet.timestamp() > 0L;
        byte[] data = packet.buffer().getBytes();

        final boolean hasPayload = data != null && data.length > 0;

        buf.writeVarInt(packetId).writeBoolean(hasTimestamp).writeBoolean(hasPayload);
        if (hasTimestamp) buf.writeVarLong(packet.timestamp());

        if (hasPayload) {
            final boolean compressed;
            if (data.length >= ZIP_THRESHOLD) {
                byte[] zipped = ResourceUtils.zipBytes(data, "zipped-buffer");
                if (zipped.length < data.length) {
                    data = zipped;
                    compressed = true;
                } else compressed = false;
            } else compressed = false;

            CRC32 crc32 = ChecksumUtils.crc32(true);
            crc32.update(ByteBuffer.allocate(4).putInt(Messenger.SEED).array());
            crc32.update(data);
            int check = (int) crc32.getValue();
            buf.writeBoolean(compressed).writeVarInt(check).writeByteArray(data);
        }

        out.writeBytes(buf.nettyBuffer());
    }
}