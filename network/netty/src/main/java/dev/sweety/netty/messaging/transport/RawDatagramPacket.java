package dev.sweety.netty.messaging.transport;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

import java.net.InetSocketAddress;

/**
 * Raw-mode-only marker packet: {@code UdpTransport.raw()} wraps every inbound datagram in one of
 * these and delivers it through the exact same {@code Messenger#onPacketReceive(ChannelHandlerContext, Packet)}
 * path TCP already uses (no separate hook) — consumers (e.g. {@code UdpSocialClient}/
 * {@code UdpSocialServer}) branch on {@code instanceof RawDatagramPacket} inside their existing
 * {@code onPacketReceive} override and parse their own HELLO/DATA wire format out of {@link #data()}.
 *
 * <p>Never routed through {@link dev.sweety.netty.packet.registry.PacketRegistry}/{@code PacketEncoder}/
 * {@code PacketDecoder} — {@link #write}/{@link #read} are no-ops, satisfying {@link Packet}'s codec
 * contract without ever being invoked (only {@code UdpTransport.packets()}'s generic codec path uses
 * the registry; this is exclusively {@code raw()} mode).
 */
public final class RawDatagramPacket extends Packet {

    private final byte[] data;
    private final InetSocketAddress sender;

    public RawDatagramPacket(byte[] data, InetSocketAddress sender) {
        this.data = data;
        this.sender = sender;
    }

    public byte[] data() {
        return data;
    }

    public InetSocketAddress sender() {
        return sender;
    }

    @Override
    public void write(BufferWriter buffer) {
        // never registry-encoded — see class javadoc
    }

    @Override
    public void read(BufferReader buffer) {
        // never registry-decoded — see class javadoc
    }
}
