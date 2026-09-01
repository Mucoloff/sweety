package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes a datagram's content with the stateless {@link PacketDecoder#decode(PacketBuffer, List, PacketRegistry)}
 * and preserves the sender's {@link InetSocketAddress} by acquiring pooled {@link AddressedPacket} instances.
 */
public class DatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final PacketRegistry packetRegistry;

    public DatagramPacketDecoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        final List<Packet> packets = new ArrayList<>(1);
        PacketDecoder.decode(new PacketBuffer(msg.content()), packets, packetRegistry);
        final InetSocketAddress sender = msg.sender();
        for (Packet p : packets) {
            out.add(AddressedPacket.acquire(p, sender));
        }
    }
}
