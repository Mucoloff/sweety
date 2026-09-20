package dev.sweety.netty.messaging.impl;

import dev.sweety.netty.core.NettyClient;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.registry.PacketRegistry;

/**
 * High-performance Dual-Transport (TCP + UDP) client connected to a single host/port.
 *
 * @deprecated Since 2.0. Prefer extending {@link NettyClient} directly.
 */
@Deprecated(since = "2.0")
public abstract class DualClient extends NettyClient {

    public DualClient(String host, int port, PacketRegistry packetRegistry) {
        super(TransportMode.DUAL, host, port, packetRegistry, -1);
    }

    public DualClient(String host, int port, PacketRegistry packetRegistry, int localPort) {
        super(TransportMode.DUAL, host, port, packetRegistry, localPort);
    }

    public DualClient(String host, int tcpPort, int udpPort, PacketRegistry packetRegistry) {
        super(host, tcpPort, udpPort, packetRegistry);
    }
}

