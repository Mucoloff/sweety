package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared zero-allocation decoding and dispatch utilities powering both TCP ({@link NettyDecoder})
 * and UDP ({@link DatagramPacketDecoder}).
 */
public final class PacketCodecSupport {

    private static final SimpleLogger LOGGER = SimpleLogger.of("packet-codec");

    /** Thread-local PacketBuffer wrapper reused across decode calls — 0 allocations. */
    public static final ThreadLocal<PacketBuffer> DECODE_WRAPPER =
            ThreadLocal.withInitial(PacketBuffer::wrapper);

    /** Thread-local list reused across decode calls — 0 allocations. */
    public static final ThreadLocal<ArrayList<Packet>> DECODE_LIST =
            ThreadLocal.withInitial(() -> new ArrayList<>(4));

    private PacketCodecSupport() {}

    /**
     * Decodes packets from the given ByteBuf into the thread-local packet list.
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
            for (int i = 0, n = packets.size(); i < n; i++) {
                Packet packet = packets.get(i);
                if (packet == null) continue;
                messenger.onPacketReceive(ctx, packet);
            }
        } else {
            out.addAll(packets);
        }
    }
}
