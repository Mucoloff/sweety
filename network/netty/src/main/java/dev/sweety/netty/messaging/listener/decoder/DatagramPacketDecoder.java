package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes datagram packets into pooled {@link AddressedPacket}s preserving sender addresses.
 * Mirrors {@link NettyDecoder} with thread-local buffer wrapping and zero heap allocations.
 */
public class DatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private static final SimpleLogger LOGGER = SimpleLogger.of("datagram-decoder");

    private static final ThreadLocal<PacketBuffer> DECODE_WRAPPER =
            ThreadLocal.withInitial(PacketBuffer::wrapper);

    private static final ThreadLocal<ArrayList<Packet>> DECODE_LIST =
            ThreadLocal.withInitial(() -> new ArrayList<>(4));

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
        final ArrayList<Packet> packets = DECODE_LIST.get();
        packets.clear();
        try {
            PacketDecoder.decode(DECODE_WRAPPER.get().wrapExternal(msg.content()), packets, packetRegistry);
        } catch (PacketDecodeException e) {
            LOGGER.warn("UDP decode exception from {} ->", msg.sender(), e);
            throw new RuntimeException(e);
        }
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
