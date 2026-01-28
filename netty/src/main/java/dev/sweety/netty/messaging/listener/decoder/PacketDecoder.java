package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.zip.CRC32C;

public class PacketDecoder {

    private final IPacketRegistry packetRegistry;

    public PacketDecoder(final IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    private boolean cantRead(final PacketBuffer in, int len) {
        if (in.readableBytes() >= len) return false;
        in.resetReaderIndex();
        return true;
    }

    public void decode(final PacketBuffer in, final List<Packet> out) throws PacketDecodeException {
        final ByteBuffer seedBuf = ByteBuffer.allocate(4).putInt(Messenger.SEED).order(ByteOrder.BIG_ENDIAN).flip();

        if (in.readableBytes() - seedBuf.remaining() < 2) return; // minimal header
        in.markReaderIndex();

        final int id = in.readVarInt();
        if (cantRead(in, 1)) return;

        final boolean hasTimestamp = in.readBoolean();
        final boolean hasPayload = in.readBoolean();
        final long timestamp;

        if (hasTimestamp) {
            if (cantRead(in, 1)) return;
            timestamp = in.readVarLong();
        } else timestamp = Messenger.timeMode.now();

        // Validate checksum
        final CRC32C crc32 = ChecksumUtils.crc32(true);
        crc32.update(seedBuf);

        final ByteBuf payloadBuf;
        if (!hasPayload) {
            payloadBuf = Unpooled.EMPTY_BUFFER;
        } else {
            final boolean compressed = in.readBoolean();


            if (cantRead(in, 1)) return;
            final int payloadLength = in.readVarInt();

            if (cantRead(in, payloadLength)) return;
            // Get a retained slice of the payload for zero-copy checksum
            final PacketBuffer slice = in.readRetainedSlice(payloadLength);
            final ByteBuf nioView = slice.nettyBuffer();

            final ByteBuffer nio = nioView.nioBuffer(0, payloadLength);
            crc32.update(nio);

            if (compressed) {
                final byte[] data = new byte[payloadLength];
                nioView.getBytes(nioView.readerIndex(), data);
                final byte[] unzipped = ResourceUtils.unzipBytes(data);
                payloadBuf = Unpooled.wrappedBuffer(unzipped);
                slice.release();
            } else {
                payloadBuf = nioView; // pass through retained slice
            }
        }


        if (cantRead(in, 1)) return;
        final int checksum = in.readVarInt();
        final int check = (int) crc32.getValue();
        if (check != checksum) {
            payloadBuf.release();
            throw new PacketDecodeException("Invalid checksum for packetId " + id);
        }

        final Packet packet;
        try {
            byte[] bytes;
            if (!payloadBuf.isReadable()) {
                bytes = new byte[0];
            } else {
                bytes = new byte[payloadBuf.readableBytes()];
                payloadBuf.getBytes(payloadBuf.readerIndex(), bytes);
            }
            packet = packetRegistry.constructPacket(id, timestamp, bytes);
        } catch (Exception e) {
            throw new PacketDecodeException("Failed to decode packet (" + id + ")", e);
        } finally {
            if (payloadBuf != Unpooled.EMPTY_BUFFER) payloadBuf.release();
        }

        out.add(packet);
    }

}
