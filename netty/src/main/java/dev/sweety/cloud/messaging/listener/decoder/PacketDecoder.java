package dev.sweety.cloud.messaging.listener.decoder;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.cloud.messaging.exception.PacketDecodeException;
import dev.sweety.cloud.messaging.model.Messenger;
import dev.sweety.cloud.packet.model.Packet;
import dev.sweety.cloud.packet.registry.IPacketRegistry;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class PacketDecoder {

    private final IPacketRegistry packetRegistry;
    public PacketDecoder(IPacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    protected void decode(ByteBuf in, List<Object> out) throws Exception {
        // minimum possible: 2 (id) + 1 (boolean ts compressed) + 4 (checksum) + 4 (data length) = 13
        if (in.readableBytes() < 13) return; // buffer incomplete: wait for more data

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

        // need 4 bytes for the checksum int
        if (in.readableBytes() < 4) {
            in.resetReaderIndex();
            return; // incomplete: wait for more data
        }
        int checksum = in.readInt();

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

        int check = ChecksumUtils.crc32Int(data, Messenger.SEED);

        if (check != checksum) {
            throw new PacketDecodeException("Invalid packet checksum for packet (" + id + "): expected " + checksum + ", got " + check);
        }

        byte[] bytes = compressed ? ResourceUtils.unzipBytes(data) : data;

        final Packet packet;

        try {
            packet = packetRegistry.constructPacket(id, timestamp, bytes);
        } catch (Exception e) {
            throw new PacketDecodeException("Failed to decode packet (" + id + ")", e);
        }

        if (compressed) System.out.println("unzipped packet with size " + packet.buffer().readableBytes());
        out.add(packet);
    }
}