package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketEncodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32C;

public class PacketEncoder {
    static final int ZIP_THRESHOLD = 256;
    final IPacketRegistry packetRegistry;

    public PacketEncoder(final IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    public void sneakyEncode(final PacketBuffer out,final Packet packet) {
        try {
            encode(packet, out);
        } catch (PacketEncodeException e) {
            e.printStackTrace(System.err);
        }
    }

    public void encode(final Packet packet, final PacketBuffer out) throws PacketEncodeException {
        int packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId < 0)
            throw new PacketEncodeException("Returned PacketId by registry is < 0");

        final boolean hasTimestamp = packet.timestamp() > 0L;
        final PacketBuffer payloadBuf = packet.buffer();
        final ByteBuf payloadNetty = payloadBuf.nettyBuffer();
        final boolean hasPayload = payloadNetty.readableBytes() > 0;

        out.writeVarInt(packetId).writeBoolean(hasTimestamp).writeBoolean(hasPayload);
        if (hasTimestamp) out.writeVarLong(packet.timestamp());

        // Compute checksum directly on ByteBuf
        CRC32C crc32 = ChecksumUtils.crc32(true);
        final ByteBuffer seedBuf = ByteBuffer.allocate(4).putInt(Messenger.SEED).order(ByteOrder.BIG_ENDIAN).flip();
        crc32.update(seedBuf);
        if (hasPayload) {
            final int readable = payloadNetty.readableBytes();
            final boolean compressed;
            ByteBuf toWrite = payloadNetty.slice(payloadNetty.readerIndex(), readable);
            toWrite.retain();
            if (readable < ZIP_THRESHOLD) {
                compressed = false;
            } else {
                // Attempt compression; only accept if beneficial
                byte[] src = new byte[readable];
                payloadNetty.getBytes(payloadNetty.readerIndex(), src);
                byte[] zipped = ResourceUtils.zipBytes(src, "zipped-buffer");
                if (zipped.length >= src.length) {
                    compressed = false;
                } else {
                    toWrite.release();
                    toWrite = Unpooled.wrappedBuffer(zipped); // Netty ByteBuf from compressed data
                    compressed = true;
                }
            }

            ByteBuffer nio = toWrite.nioBuffer(0, toWrite.readableBytes());
            crc32.update(nio);

            out.writeBoolean(compressed).writeVarInt(toWrite.readableBytes());
            // Write payload bytes zero-copy where possible
            out.nettyBuffer().writeBytes(toWrite, toWrite.readableBytes());
            toWrite.release();
        }

        int check = (int) crc32.getValue();
        out.writeVarInt(check);
    }
}
