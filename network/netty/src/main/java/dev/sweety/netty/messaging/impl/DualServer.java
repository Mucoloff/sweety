package dev.sweety.netty.messaging.impl;

import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.messaging.transport.EndpointRegistry;
import dev.sweety.netty.messaging.transport.TcpTransport;
import dev.sweety.netty.messaging.transport.UdpTransport;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Hybrid dual-transport server that binds both TCP and UDP on the same port.
 *
 * <p>Receives both reliable TCP stream packets and fast connectionless UDP datagrams into the same
 * {@link #onPacketReceive(ChannelHandlerContext, Packet)} method, with built-in {@link EndpointRegistry}
 * for session and sequence correlation.
 */
public abstract class DualServer {

    protected final SimpleLogger logger = SimpleLogger.of(getClass());

    private final String host;
    private final int port;
    private final PacketRegistry packetRegistry;
    private final EndpointRegistry endpointRegistry = new EndpointRegistry();

    private final Server tcpServer;
    private final Server udpServer;

    public DualServer(String host, int port, PacketRegistry packetRegistry) {
        this.host = host;
        this.port = port;
        this.packetRegistry = packetRegistry;

        this.tcpServer = new Server(TcpTransport.INSTANCE, host, port, packetRegistry) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
                DualServer.this.onPacketReceive(ctx, packet);
            }

            @Override
            public void exception(ChannelHandlerContext ctx, Throwable throwable) {
                DualServer.this.exception(ctx, throwable);
            }

            @Override
            public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
                DualServer.this.join(ctx, promise);
            }

            @Override
            public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
                DualServer.this.quit(ctx, promise);
            }
        };

        this.udpServer = new Server(UdpTransport.packets(), host, port, packetRegistry) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
                DualServer.this.onPacketReceive(ctx, packet);
            }

            @Override
            public void exception(ChannelHandlerContext ctx, Throwable throwable) {
                DualServer.this.exception(ctx, throwable);
            }

            @Override
            public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
                promise.setSuccess();
            }
        };
    }

    public abstract void onPacketReceive(ChannelHandlerContext ctx, Packet packet);

    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("DualServer exception: ", throwable);
    }

    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        tcpServer.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        Long connectionId = Server.connectionIdOf(ctx);
        if (connectionId != null) {
            endpointRegistry.remove(connectionId);
        }
        promise.setSuccess();
    }

    public void start() {
        tcpServer.start();
        udpServer.start();
        logger.info("DualServer (TCP + UDP) listening on {}:{}", host, port);
    }

    public void stop() {
        tcpServer.stop();
        udpServer.stop();
    }

    public Server tcpServer() {
        return tcpServer;
    }

    public Server udpServer() {
        return udpServer;
    }

    public EndpointRegistry endpointRegistry() {
        return endpointRegistry;
    }

    /**
     * Sends a packet reliably over TCP.
     */
    public CompletableFuture<ChannelFuture> sendTcp(ChannelHandlerContext ctx, Packet packet) {
        return tcpServer.sendPacket(ctx, packet);
    }

    /**
     * Sends a fast datagram over UDP to the specified recipient.
     */
    public CompletableFuture<Void> sendUdp(InetSocketAddress recipient, Packet packet) {
        if (udpServer.channel() != null && udpServer.channel().isActive()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            udpServer.channel().writeAndFlush(new AddressedPacket(packet, recipient)).addListener(f -> {
                if (f.isSuccess()) future.complete(null);
                else future.completeExceptionally(f.cause());
            });
            return future;
        }
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("UDP server channel is not active"));
        return failed;
    }
}
