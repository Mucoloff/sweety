package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.packet.model.Packet;

import java.net.InetSocketAddress;

/** Outbound message for {@link UdpTransport#packets()}: a datagram write needs an explicit destination. */
public record AddressedPacket(Packet packet, InetSocketAddress recipient) {
}
