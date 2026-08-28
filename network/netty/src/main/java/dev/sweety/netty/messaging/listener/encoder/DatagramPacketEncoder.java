package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * Encodes an {@link AddressedPacket} with the stateless {@link PacketEncoder#encode(dev.sweety.netty.packet.model.Packet, PacketBuffer, PacketRegistry)}
 * static (no delta-timestamp state — see {@link DatagramPacketDecoder}'s javadoc for the same reasoning).
 */
public class DatagramPacketEncoder extends MessageToMessageEncoder<AddressedPacket> {

    private final PacketRegistry packetRegistry;

    public DatagramPacketEncoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AddressedPacket msg, List<Object> out) throws Exception {
        PacketBuffer buffer = PacketBufferAllocator.DEFAULT.buffer();
        PacketEncoder.encode(msg.packet(), buffer, packetRegistry);
        out.add(new DatagramPacket(buffer.nettyBuffer(), msg.recipient()));
    }
}
