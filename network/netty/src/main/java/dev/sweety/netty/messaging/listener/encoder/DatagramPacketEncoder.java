package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.netty.messaging.listener.PacketCodecSupport;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * UDP datagram packet encoder. Delegates to {@link PacketCodecSupport} for safe MTU checks and pool recycling.
 */
public class DatagramPacketEncoder extends MessageToMessageEncoder<AddressedPacket> {

    private final PacketRegistry packetRegistry;

    public DatagramPacketEncoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AddressedPacket msg, List<Object> out) throws Exception {
        out.add(PacketCodecSupport.encodeDatagram(msg, this.packetRegistry));
    }
}
