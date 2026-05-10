package dev.sweety.netty;

import dev.sweety.netty.messaging.impl.GenericClient;
import dev.sweety.netty.messaging.impl.GenericServer;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.SimplePacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.BiConsumer;

public class Netty {

    public static ServerBuilder server() {
        return new ServerBuilder();
    }

    public static ClientBuilder client() {
        return new ClientBuilder();
    }

    public static class ServerBuilder {
        private String host = "127.0.0.1";
        private int port = 8080;
        private IPacketRegistry registry = new SimplePacketRegistry();
        private BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler;
        private BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler;
        private BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler;
        private BiConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet> packetReceiveHandler;
        private dev.sweety.math.function.TriConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet, Boolean> packetSendHandler;

        public ServerBuilder host(String host) {
            this.host = host;
            return this;
        }

        public ServerBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ServerBuilder registry(IPacketRegistry registry) {
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

        public ServerBuilder onPacketReceive(BiConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet> packetReceiveHandler) {
            this.packetReceiveHandler = packetReceiveHandler;
            return this;
        }

        public ServerBuilder onPacketSend(dev.sweety.math.function.TriConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet, Boolean> packetSendHandler) {
            this.packetSendHandler = packetSendHandler;
            return this;
        }

        public GenericServer build() {
            GenericServer server = new GenericServer(host, port, registry);
            server.setJoinHandler(joinHandler);
            server.setQuitHandler(quitHandler);
            server.setExceptionHandler(exceptionHandler);
            server.setPacketReceiveHandler(packetReceiveHandler);
            server.setPacketSendHandler(packetSendHandler);
            return server;
        }
    }

    public static class ClientBuilder {
        private String host = "127.0.0.1";
        private int port = 8080;
        private int localPort = -1;
        private IPacketRegistry registry = new SimplePacketRegistry();
        private BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler;
        private BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler;
        private BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler;
        private BiConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet> packetReceiveHandler;
        private dev.sweety.math.function.TriConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet, Boolean> packetSendHandler;

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

        public ClientBuilder registry(IPacketRegistry registry) {
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

        public ClientBuilder onPacketReceive(BiConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet> packetReceiveHandler) {
            this.packetReceiveHandler = packetReceiveHandler;
            return this;
        }

        public ClientBuilder onPacketSend(dev.sweety.math.function.TriConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet, Boolean> packetSendHandler) {
            this.packetSendHandler = packetSendHandler;
            return this;
        }

        public GenericClient build() {
            GenericClient client = new GenericClient(host, port, registry, localPort);
            client.setJoinHandler(joinHandler);
            client.setQuitHandler(quitHandler);
            client.setExceptionHandler(exceptionHandler);
            client.setPacketReceiveHandler(packetReceiveHandler);
            client.setPacketSendHandler(packetSendHandler);
            return client;
        }
    }
}
