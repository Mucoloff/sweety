package dev.sweety.netty;

import dev.sweety.math.function.TriConsumer;
import dev.sweety.netty.messaging.impl.GenericClient;
import dev.sweety.netty.messaging.impl.GenericServer;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.BiConsumer;

/**
 * Fluent builder DSL and factory utilities for Sweety high-performance Netty services (TCP, UDP, DUAL).
 */
public final class Netty {

    private Netty() {}

    public static ServerBuilder server() {
        return new ServerBuilder();
    }

    public static ClientBuilder client() {
        return new ClientBuilder();
    }

    public static ServerBuilder tcpServer(String host, int port, PacketRegistry registry) {
        return server().tcp().host(host).port(port).registry(registry);
    }

    public static ServerBuilder udpServer(String host, int port, PacketRegistry registry) {
        return server().udp().host(host).port(port).registry(registry);
    }

    public static ServerBuilder dualServer(String host, int port, PacketRegistry registry) {
        return server().dual().host(host).port(port).registry(registry);
    }

    public static ClientBuilder tcpClient(String host, int port, PacketRegistry registry) {
        return client().tcp().host(host).port(port).registry(registry);
    }

    public static ClientBuilder udpClient(String host, int port, PacketRegistry registry) {
        return client().udp().host(host).port(port).registry(registry);
    }

    public static ClientBuilder dualClient(String host, int port, PacketRegistry registry) {
        return client().dual().host(host).port(port).registry(registry);
    }

    public static class ServerBuilder {
        private TransportMode mode = TransportMode.TCP;
        private String host = "127.0.0.1";
        private int port = 8080;
        private PacketRegistry registry = new OptimizedPacketRegistry();
        private BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler;
        private BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler;
        private BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler;
        private BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler;
        private TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler;

        public ServerBuilder mode(TransportMode mode) {
            this.mode = mode;
            return this;
        }

        public ServerBuilder tcp() {
            return mode(TransportMode.TCP);
        }

        public ServerBuilder udp() {
            return mode(TransportMode.UDP);
        }

        public ServerBuilder dual() {
            return mode(TransportMode.DUAL);
        }

        public ServerBuilder host(String host) {
            this.host = host;
            return this;
        }

        public ServerBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ServerBuilder registry(PacketRegistry registry) {
            this.registry = registry;
            return this;
        }

        public ServerBuilder onJoin(BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler) {
            this.joinHandler = joinHandler;
            return this;
        }

        public ServerBuilder onQuit(BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler) {
            this.quitHandler = quitHandler;
            return this;
        }

        public ServerBuilder onException(BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public ServerBuilder onReceive(BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler) {
            this.packetReceiveHandler = packetReceiveHandler;
            return this;
        }

        public ServerBuilder onPacketReceive(BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler) {
            return onReceive(packetReceiveHandler);
        }

        public ServerBuilder onSend(TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler) {
            this.packetSendHandler = packetSendHandler;
            return this;
        }

        public ServerBuilder onPacketSend(TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler) {
            return onSend(packetSendHandler);
        }

        public GenericServer build() {
            GenericServer server = new GenericServer(mode, host, port, registry);
            server.setJoinHandler(joinHandler);
            server.setQuitHandler(quitHandler);
            server.setExceptionHandler(exceptionHandler);
            server.setPacketReceiveHandler(packetReceiveHandler);
            server.setPacketSendHandler(packetSendHandler);
            return server;
        }
    }

    public static class ClientBuilder {
        private TransportMode mode = TransportMode.TCP;
        private String host = "127.0.0.1";
        private int port = 8080;
        private int localPort = -1;
        private PacketRegistry registry = new OptimizedPacketRegistry();
        private BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler;
        private BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler;
        private BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler;
        private BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler;
        private TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler;

        public ClientBuilder mode(TransportMode mode) {
            this.mode = mode;
            return this;
        }

        public ClientBuilder tcp() {
            return mode(TransportMode.TCP);
        }

        public ClientBuilder udp() {
            return mode(TransportMode.UDP);
        }

        public ClientBuilder dual() {
            return mode(TransportMode.DUAL);
        }

        public ClientBuilder host(String host) {
            this.host = host;
            return this;
        }

        public ClientBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ClientBuilder localPort(int localPort) {
            this.localPort = localPort;
            return this;
        }

        public ClientBuilder registry(PacketRegistry registry) {
            this.registry = registry;
            return this;
        }

        public ClientBuilder onJoin(BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler) {
            this.joinHandler = joinHandler;
            return this;
        }

        public ClientBuilder onQuit(BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler) {
            this.quitHandler = quitHandler;
            return this;
        }

        public ClientBuilder onException(BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public ClientBuilder onReceive(BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler) {
            this.packetReceiveHandler = packetReceiveHandler;
            return this;
        }

        public ClientBuilder onPacketReceive(BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler) {
            return onReceive(packetReceiveHandler);
        }

        public ClientBuilder onSend(TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler) {
            this.packetSendHandler = packetSendHandler;
            return this;
        }

        public ClientBuilder onPacketSend(TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler) {
            return onSend(packetSendHandler);
        }

        public GenericClient build() {
            GenericClient client = new GenericClient(mode, host, port, registry, localPort);
            client.setJoinHandler(joinHandler);
            client.setQuitHandler(quitHandler);
            client.setExceptionHandler(exceptionHandler);
            client.setPacketReceiveHandler(packetReceiveHandler);
            client.setPacketSendHandler(packetSendHandler);
            return client;
        }
    }
}
