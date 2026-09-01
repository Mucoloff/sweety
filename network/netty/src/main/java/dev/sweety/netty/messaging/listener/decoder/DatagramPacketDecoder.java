package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * UDP datagram packet decoder. Uses {@link PacketCodecSupport} for stateless zero-allocation decoding
 * and wraps packets in pooled {@link AddressedPacket}s preserving sender addresses.
 */
public class DatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final PacketRegistry packetRegistry;
    private final Messenger messenger;

    public DatagramPacketDecoder(PacketRegistry packetRegistry) {
        this(packetRegistry, null);
    }

    public DatagramPacketDecoder(PacketRegistry packetRegistry, Messenger messenger) {
        this.packetRegistry = packetRegistry;
        this.messenger = messenger;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
        final ArrayList<Packet> packets = PacketCodecSupport.decodeStateless(msg.content(), this.packetRegistry, msg.sender());
        if (packets.isEmpty()) return;

        final InetSocketAddress sender = msg.sender();
        for (int i = 0, n = packets.size(); i < n; i++) {
            Packet p = packets.get(i);
            if (p == null) continue;
            AddressedPacket addressed = AddressedPacket.acquire(p, sender);
            if (this.messenger != null) {
                this.messenger.onPacketReceive(ctx, addressed);
            } else {
                out.add(addressed);
            }
        }
    }
}
