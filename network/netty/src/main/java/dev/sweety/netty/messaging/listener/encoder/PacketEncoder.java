package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.data.ChecksumUtils;
import dev.sweety.data.buffer.BufferPool;
import dev.sweety.data.compress.CompressUtils;
import dev.sweety.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketEncodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;
import java.util.zip.Deflater;

public class PacketEncoder {
    static final int ZIP_THRESHOLD = 256;

    private static final ByteBuffer SEED_BUFFER = ByteBuffer.wrap(new byte[]{
            (byte) (Messenger.SEED >>> 24), (byte) (Messenger.SEED >>> 16),
            (byte) (Messenger.SEED >>> 8),  (byte)  Messenger.SEED
    }).asReadOnlyBuffer();
    final IPacketRegistry packetRegistry;

    public PacketEncoder(final IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    public void encode(final Packet packet, final PacketBuffer out) throws PacketEncodeException {
        encode(packet, out, this.packetRegistry);
    }

    public static void encode(final Packet packet, final PacketBuffer out, final IPacketRegistry packetRegistry) throws PacketEncodeException {
        int packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId == -1)
            throw new PacketEncodeException("Returned PacketId by registry is invalid (-1)");

        final boolean hasTimestamp = packet.hasTimestamp();
        final PacketBuffer payloadBuf = packet.buffer();
        final ByteBuf payloadNetty = payloadBuf.nettyBuffer();
        final int readable = payloadNetty.readableBytes();
        final boolean hasPayload = readable > 0;

        out.writeVarInt(packetId).writeBoolean(hasTimestamp).writeBoolean(hasPayload);
        if (hasTimestamp) out.writeVarLong(packet.timestamp());

        // Compute checksum directly on ByteBuf
        CRC32C crc32 = ChecksumUtils.crc32(true);
        crc32.update(SEED_BUFFER.duplicate());

        if (hasPayload) {
            final boolean compressed;
            ByteBuf toWrite = payloadNetty.slice(payloadNetty.readerIndex(), readable);
            toWrite.retain();

            if (readable < ZIP_THRESHOLD) {
                compressed = false;
            } else {
                byte[] src = BufferPool.DEFAULT.borrowBytes(readable);
                byte[] dst = BufferPool.DEFAULT.borrowBytes(readable);
                try {
                    payloadNetty.getBytes(payloadNetty.readerIndex(), src, 0, readable);
                    Deflater deflater = BufferPool.DEFAULT.acquireDeflater();
                    int compressedLen = CompressUtils.deflate(src, readable, dst, deflater);
                    if (compressedLen < 0 || compressedLen >= readable) {
                        compressed = false;
                    } else {
                        byte[] exact = new byte[compressedLen];
                        System.arraycopy(dst, 0, exact, 0, compressedLen);
                        toWrite.release();
                        toWrite = Unpooled.wrappedBuffer(exact); // zero-copy wrap
                        compressed = true;
                    }
                } finally {
                    BufferPool.DEFAULT.returnBytes(src);
                    BufferPool.DEFAULT.returnBytes(dst);
                }
            }

            ByteBuffer nio = toWrite.nioBuffer(0, toWrite.readableBytes());
            crc32.update(nio);

            if (compressed) {
                out.writeBoolean(true).writeVarInt(readable).writeVarInt(toWrite.readableBytes());
            } else {
                out.writeBoolean(false).writeVarInt(toWrite.readableBytes());
            }
            out.nettyBuffer().writeBytes(toWrite, toWrite.readableBytes());
            toWrite.release();
        }

        int check = (int) crc32.getValue();
        out.writeVarInt(check);
    }
}
