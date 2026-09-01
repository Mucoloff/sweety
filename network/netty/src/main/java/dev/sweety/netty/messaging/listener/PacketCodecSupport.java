package dev.sweety.netty.messaging.listener;

import dev.sweety.exception.PacketDecodeException;
import dev.sweety.netty.messaging.listener.encoder.*;
import dev.sweety.netty.messaging.listener.decoder.*;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared zero-allocation codec and dispatch utilities powering both TCP ({@link NettyDecoder}/{@link dev.sweety.netty.messaging.listener.encoder.NettyEncoder})
 * and UDP ({@link DatagramPacketDecoder}/{@link dev.sweety.netty.messaging.listener.encoder.DatagramPacketEncoder}).
 */
public final class PacketCodecSupport {

    private static final SimpleLogger LOGGER = SimpleLogger.of("packet-codec");

    public static final int SAFE_UDP_MTU = 1400;

    /** Thread-local PacketBuffer wrapper reused across decode calls — 0 allocations. */
    public static final ThreadLocal<PacketBuffer> DECODE_WRAPPER =
            ThreadLocal.withInitial(PacketBuffer::wrapper);

    /** Thread-local list reused across decode calls — 0 allocations. */
    public static final ThreadLocal<ArrayList<Packet>> DECODE_LIST =
            ThreadLocal.withInitial(() -> new ArrayList<>(4));

    /** Thread-local PacketBuffer wrapper reused across encode calls — 0 allocations. */
    public static final ThreadLocal<PacketBuffer> ENCODE_WRAPPER =
            ThreadLocal.withInitial(PacketBuffer::wrapper);

    private PacketCodecSupport() {}

    /**
     * Decodes packets from the given ByteBuf into the thread-local packet list (TCP stream).
     */
    public static ArrayList<Packet> decodePackets(PacketDecoder decoder, ByteBuf in, Object source) {
        final ArrayList<Packet> packets = DECODE_LIST.get();
        packets.clear();
        try {
            decoder.decode(DECODE_WRAPPER.get().wrapExternal(in), packets);
        } catch (PacketDecodeException e) {
            LOGGER.warn("decode exception from {} ->", source, e);
            throw new RuntimeException(e);
        }
        return packets;
    }

    /**
     * Stateless one-shot packet decoding for connectionless / UDP datagrams.
     */
    public static ArrayList<Packet> decodeStateless(ByteBuf in, PacketRegistry registry, Object source) {
        final ArrayList<Packet> packets = DECODE_LIST.get();
        packets.clear();
        try {
            PacketDecoder.decode(DECODE_WRAPPER.get().wrapExternal(in), packets, registry);
        } catch (PacketDecodeException e) {
            LOGGER.warn("decode exception from {} ->", source, e);
            throw new RuntimeException(e);
        }
        return packets;
    }

    /**
     * Fast-path dispatch: delivers directly to Messenger if present, otherwise adds to downstream Netty pipeline output.
     */
    public static void dispatch(ChannelHandlerContext ctx, List<Packet> packets, Messenger messenger, List<Object> out) {
        if (packets.isEmpty()) return;

        if (messenger != null) {
            for (Packet packet : packets) {
                if (packet == null) continue;
                messenger.onPacketReceive(ctx, packet);
            }
        } else {
            out.addAll(packets);
        }
    }

    /**
     * Encodes a packet into a stream ByteBuf (TCP).
     */
    public static void encodeStream(PacketEncoder encoder, Packet packet, ByteBuf out) throws dev.sweety.netty.messaging.exception.PacketEncodeException {
        encoder.encode(packet, ENCODE_WRAPPER.get().wrapExternal(out));
    }

    /**
     * Encodes an AddressedPacket into a Netty DatagramPacket (UDP) with safe MTU check and auto-release.
     */
    public static DatagramPacket encodeDatagram(AddressedPacket msg, PacketRegistry registry) throws dev.sweety.netty.messaging.exception.PacketEncodeException {
        final PacketBuffer buffer = PacketBufferAllocator.DEFAULT.buffer();
        try {
            PacketEncoder.encode(msg.packet(), buffer, registry);

            final int bytes = buffer.readableBytes();
            if (bytes > SAFE_UDP_MTU) {
                LOGGER.warn("UDP datagram payload exceeds safe MTU ({} > {} bytes) for recipient {}",
                        bytes, SAFE_UDP_MTU, msg.recipient());
            }

            return new DatagramPacket(buffer.nettyBuffer(), msg.recipient());
        } finally {
            msg.release();
        }
    }
}
