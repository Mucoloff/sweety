package dev.sweety.network.cloud.messaging;

import dev.sweety.network.cloud.messaging.model.Messenger;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Server extends Messenger<ServerBootstrap> {
    private final Map<SocketAddress, ChannelHandlerContext> clients = new ConcurrentHashMap<>();

    public Server(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(new ServerBootstrap(), host, port, packetRegistry, packets);
    }

    public void send(ChannelHandlerContext ctx, Packet... msgs) {
        final Channel channel = ctx.channel();
        if (!channel.isActive()) return;
        for (Packet msg : msgs) {
            channel.write(msg);
        }
        channel.flush();
    }

    public void send(ChannelHandlerContext ctx, Packet msg) {
        final Channel channel = ctx.channel();
        if (!channel.isActive()) return;
        channel.writeAndFlush(msg);
    }

    public void sendAll(final Packet... msgs) {
        if (clients.isEmpty()) return;
        this.clients.values().forEach((ctx) -> send(ctx, msgs));
    }

    public void sendAll(final Packet msg) {
        if (clients.isEmpty()) return;
        this.clients.values().forEach((ctx) -> {
            final Channel channel = ctx.channel();
            if (!channel.isActive()) return;
            channel.writeAndFlush(msg);
        });
    }

    public void addClient(ChannelHandlerContext ctx, SocketAddress address) {
        this.clients.put(address, ctx);
    }

    public void removeClient(SocketAddress address) {
        this.clients.remove(address);
    }

}


