package dev.sweety.netty.messaging.impl;

import dev.sweety.netty.core.NettyServer;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.registry.PacketRegistry;

/**
 * High-performance Dual-Transport (TCP + UDP) server running on a single shared EventLoopGroup.
 *
 * @deprecated Since 2.0. Prefer extending {@link NettyServer} directly.
 */
@Deprecated(since = "2.0")
public abstract class DualServer extends NettyServer {

    public DualServer(String host, int port, PacketRegistry packetRegistry) {
        super(TransportMode.DUAL, host, port, packetRegistry);
    }

    public DualServer(String host, int tcpPort, int udpPort, PacketRegistry packetRegistry) {
        super(host, tcpPort, udpPort, packetRegistry);
    }
}

