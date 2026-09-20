package dev.sweety.netty.core;

import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.transport.EndpointRegistry;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extensible, high-performance base Netty Server supporting:
 * <ul>
 *     <li>Standalone TCP</li>
 *     <li>Standalone UDP</li>
 *     <li>Dual-Transport with shared port (TCP and UDP on the same port number)</li>
 *     <li>Dual-Transport with distinct ports (e.g. TCP on port X, UDP on port Y)</li>
 * </ul>
 * Provides protected lifecycle hooks for clean subclassing.
 */
public abstract class NettyServer extends Server {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final EndpointRegistry endpointRegistry = new EndpointRegistry();

    public NettyServer(String host, int port, PacketRegistry packetRegistry) {
        super(TransportMode.TCP, host, port, packetRegistry);
    }

    public NettyServer(TransportMode mode, String host, int port, PacketRegistry packetRegistry) {
        super(mode, host, port, packetRegistry);
    }

    public NettyServer(String host, int tcpPort, int udpPort, PacketRegistry packetRegistry) {
        super(TransportMode.DUAL, host, tcpPort, packetRegistry);
        udpPort(udpPort);
    }

    public EndpointRegistry endpointRegistry() {
        return endpointRegistry;
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (ctx.channel().remoteAddress() != null) {
            addClient(ctx, ctx.channel().remoteAddress());
        }
        onChannelActive(ctx);
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        onChannelInactive(ctx);
        promise.setSuccess();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        onException(ctx, throwable);
    }

    // Protected lifecycle hooks for custom domain logic in subclasses
    protected void onChannelActive(ChannelHandlerContext ctx) {}

    protected void onChannelInactive(ChannelHandlerContext ctx) {}

    protected void onException(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in NettyServer pipeline for channel {}: ", ctx.channel(), cause);
    }
}
