package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32;

public class PacketDecoder {

    private final IPacketRegistry packetRegistry;
    public PacketDecoder(IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    public void decode(ByteBuf in, List<Packet> out) throws PacketDecodeException {
        // minimum possible: 2 (id) + 1 (boolean ts compressed) + 8 (checksum long) + 4 (data length) = 15
        // if timestamp present add 8 more bytes
        if (in.readableBytes() < 15) return; // buffer incomplete: wait for more data

        in.markReaderIndex();
        short id = in.readShort();
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

        // need 8 bytes for the checksum long
        if (in.readableBytes() < 8) {
            in.resetReaderIndex();
            return; // incomplete: wait for more data
        }
        long checksum = in.readLong();

        // need 4 bytes for the data length int
        if (in.readableBytes() < 4) {
            in.resetReaderIndex();
            return; // incomplete: wait for more data
        }
        int length = in.readInt();

        // validate length
        if (length < 0) {
            // invalid or too large - close the connection to avoid abuse
            throw new PacketDecodeException("Invalid packet length: " + length + ", closing connection.");
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return; // incomplete payload: wait for more data
        }
        byte[] data = new byte[length];
        in.readBytes(data);

        CRC32 crc32 = ChecksumUtils.crc32(true);
        crc32.update(ByteBuffer.allocate(4).putInt(Messenger.SEED).array());
        crc32.update(data);
        long check = crc32.getValue();

        byte[] bytes = compressed ? ResourceUtils.unzipBytes(data) : data;

        final Packet packet;

        try {
            packet = packetRegistry.constructPacket(id, timestamp, bytes);
        } catch (Exception e) {
            throw new PacketDecodeException("Failed to decode packet (" + id + ")", e);
        }

        // Diagnostics for MetricsUpdatePacket
        if (packet.getClass().getSimpleName().equals("MetricsUpdatePacket")) {
            System.out.println("[DEC] Metrics packet: len=" + length + ", compressed=" + compressed + ", checksum(read)=" + checksum + ", checksum(calc)=" + check + " valid: " + (check == checksum));
        }

        if (check != checksum && false) {
            throw new PacketDecodeException("Invalid packet checksum for packet (" + packet + "): expected " + checksum + ", got " + check);
        }

        if (compressed) System.out.println("unzipped packet with size " + packet.buffer().readableBytes());
        out.add(packet);
    }
}