package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Encodes an {@link AddressedPacket} with the stateless {@link PacketEncoder#encode}
 * and enforces safe MTU limits. Automatically recycles the {@link AddressedPacket} to its pool.
 */
public class DatagramPacketEncoder extends MessageToMessageEncoder<AddressedPacket> {

    public static final int SAFE_UDP_MTU = 1400;
    private static final Logger LOGGER = LoggerFactory.getLogger(DatagramPacketEncoder.class);

    private final PacketRegistry packetRegistry;

    public DatagramPacketEncoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AddressedPacket msg, List<Object> out) throws Exception {
        final PacketBuffer buffer = PacketBufferAllocator.DEFAULT.buffer();
        try {
            PacketEncoder.encode(msg.packet(), buffer, packetRegistry);

            final int bytes = buffer.readableBytes();
            if (bytes > SAFE_UDP_MTU) {
                LOGGER.warn("UDP datagram payload exceeds safe MTU ({} > {} bytes) for recipient {}",
                        bytes, SAFE_UDP_MTU, msg.recipient());
            }

            out.add(new DatagramPacket(buffer.nettyBuffer(), msg.recipient()));
        } finally {
            msg.release();
        }
    }
}
