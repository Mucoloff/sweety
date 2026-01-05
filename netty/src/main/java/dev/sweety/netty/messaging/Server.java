package dev.sweety.netty.messaging;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Server extends Messenger<ServerBootstrap> {
    @Getter
    private final Map<SocketAddress, ChannelHandlerContext> clients = new ConcurrentHashMap<>();

    public Server(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(new ServerBootstrap(), host, port, packetRegistry, packets);
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
    }

    public void removeClient(SocketAddress address) {
        this.clients.remove(address);
    }

}


