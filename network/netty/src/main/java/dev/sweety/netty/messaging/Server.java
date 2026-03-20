package dev.sweety.netty.messaging;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.sweety.record.annotations.RecordGetter;

public abstract class Server extends Messenger<ServerBootstrap> {

    @RecordGetter
    private final Map<SocketAddress, ChannelHandlerContext> clients;

    public Server(String host, int port, IPacketRegistry packetRegistry, Map<SocketAddress, ChannelHandlerContext> clients) {
        super(new ServerBootstrap(), host, port, packetRegistry, -1);
        this.clients = clients;
    }

    public Server(String host, int port, IPacketRegistry packetRegistry) {
        this(host, port, packetRegistry, new ConcurrentHashMap<>());
    }

    public void broadcastPacket(final Packet msg) {
        if (clients.isEmpty()) return;
        this.clients.values().forEach((ctx) -> sendPacket(ctx, msg));
    }

    public void broadcastPacket(final Packet... msgs) {
        if (clients.isEmpty()) return;
        this.clients.values().forEach((ctx) -> sendPacket(ctx, msgs));
    }

    public void addClient(ChannelHandlerContext ctx, SocketAddress address) {
        this.clients.put(address, ctx);
        ctx.channel().closeFuture().addListener(f -> removeClient(address));
    }

    public void removeClient(SocketAddress address) {
        this.clients.remove(address);
    }

}