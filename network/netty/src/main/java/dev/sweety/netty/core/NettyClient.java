package dev.sweety.netty.core;

import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Extensible, high-performance base Netty Client supporting:
 * <ul>
 *     <li>Standalone TCP</li>
 *     <li>Standalone UDP</li>
 *     <li>Dual-Transport with shared port (connecting to remote host:port for both TCP and UDP)</li>
 *     <li>Dual-Transport with distinct ports</li>
 * </ul>
 * Provides protected lifecycle hooks and integrated {@link AutoReconnect}.
 */
public abstract class NettyClient extends Client {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final AutoReconnect autoReconnect = new AutoReconnect(5, java.util.concurrent.TimeUnit.SECONDS, this::start);

    public NettyClient(String host, int port, PacketRegistry packetRegistry) {
        this(TransportMode.TCP, host, port, packetRegistry, -1);
    }

    public NettyClient(TransportMode mode, String host, int port, PacketRegistry packetRegistry) {
        this(mode, host, port, packetRegistry, -1);
    }

    public NettyClient(TransportMode mode, String host, int port, PacketRegistry packetRegistry, int localPort) {
        super(mode, host, port, packetRegistry, localPort);
    }

    public NettyClient(String host, int tcpPort, int udpPort, PacketRegistry packetRegistry) {
        super(TransportMode.DUAL, host, tcpPort, packetRegistry, -1);
        udpPort(udpPort);
    }

    public AutoReconnect autoReconnect() {
        return autoReconnect;
    }

    public CompletableFuture<?> startAsync() {
        return connect();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        onConnected(ctx);
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        onDisconnected(ctx);
        promise.setSuccess();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        onException(ctx, throwable);
    }

    // Protected lifecycle hooks for custom domain logic in subclasses
    protected void onConnected(ChannelHandlerContext ctx) {}

    protected void onDisconnected(ChannelHandlerContext ctx) {}

    protected void onException(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in NettyClient pipeline for channel {}: ", ctx.channel(), cause);
    }
}
