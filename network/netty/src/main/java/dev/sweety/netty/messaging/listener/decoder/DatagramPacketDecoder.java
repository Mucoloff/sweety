package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes a datagram's content with the stateless {@link PacketDecoder#decode(PacketBuffer, List, PacketRegistry)}
 * static — not the stateful per-connection delta-timestamp instance path, which assumes ordered
 * delivery and is unsafe over UDP.
 */
public class DatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final PacketRegistry packetRegistry;

    public DatagramPacketDecoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        List<Packet> packets = new ArrayList<>(1);
        PacketDecoder.decode(new PacketBuffer(msg.content()), packets, packetRegistry);
        out.addAll(packets);
    }
}
