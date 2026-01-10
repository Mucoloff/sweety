package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32;

public class PacketDecoder {

    private final IPacketRegistry packetRegistry;
    public PacketDecoder(final IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    public void decode(final PacketBuffer in,final  List<Packet> out) throws PacketDecodeException {
        if (in.readableBytes() < 2) return; // id + flags
        in.markReaderIndex();

        final int id = in.readVarInt();

        if (in.readableBytes() < 1) {
            in.resetReaderIndex();
            return;
        }

        final boolean hasTimestamp = in.readBoolean();
        final boolean hasPayload = in.readBoolean();

        final long timestamp;
        if (hasTimestamp) {
            if (in.readableBytes() < 1) {
                in.resetReaderIndex();
                return;
            }
            timestamp = in.readVarLong();
        } else timestamp = System.currentTimeMillis();

        final byte[] bytes;

        if (hasPayload) {
            final boolean compressed = in.readBoolean();
            if (in.readableBytes() < 1) {
                in.resetReaderIndex();
                return;
            }
            final int checksum = in.readVarInt();

            if (in.readableBytes() < 1) {
                in.resetReaderIndex();
                return;
            }

            final byte[] data = in.readByteArray();

            final CRC32 crc32 = ChecksumUtils.crc32(true);
            crc32.update(ByteBuffer.allocate(4).putInt(Messenger.SEED).array());
            crc32.update(data);
            final int check = (int) crc32.getValue();

            if (check != checksum)
                throw new PacketDecodeException("Invalid checksum for packetId " + id);

            bytes = compressed ? ResourceUtils.unzipBytes(data) : data;
        } else bytes = new byte[0];

        final Packet packet;

        try {
            packet = packetRegistry.constructPacket(id, timestamp, bytes);
        } catch (Exception e) {
            throw new PacketDecodeException("Failed to decode packet (" + id + ")", e);
        }

        out.add(packet);
    }

}