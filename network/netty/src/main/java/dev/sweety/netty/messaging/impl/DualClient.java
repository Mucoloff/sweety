package dev.sweety.netty.messaging.impl;

import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance Dual-Transport (TCP + UDP) client connected to a single host/port.
 */
public abstract class DualClient extends Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(DualClient.class);

    public DualClient(String host, int port, PacketRegistry packetRegistry) {
        super(TransportMode.DUAL, host, port, packetRegistry, -1);
    }

    public DualClient(String host, int port, PacketRegistry packetRegistry, int localPort) {
        super(TransportMode.DUAL, host, port, packetRegistry, localPort);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        promise.setSuccess();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        LOGGER.error("DualClient exception: ", throwable);
    }
}
