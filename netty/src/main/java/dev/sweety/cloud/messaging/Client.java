package dev.sweety.cloud.messaging;

import dev.sweety.cloud.messaging.model.Messenger;
import dev.sweety.cloud.packet.model.Packet;
import dev.sweety.cloud.packet.registry.IPacketRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

public abstract class Client extends Messenger<Bootstrap> {

    public Client(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(new Bootstrap(), host, port, packetRegistry, packets);
    }

    public CompletableFuture<Void> sendPacket(Packet packet) {
        return super.sendPacket(channelContext(), packet);
    }

    public CompletableFuture<Void> sendPacket(Packet... packets) {
        return sendPacket(channelContext(), packets);
    }

    public CompletableFuture<Void> writePacket(Packet packet) {
        return super.writePacket(channelContext(), packet);
    }

    public CompletableFuture<Void> writePacket(Packet... packets) {
        return super.writePacket(channelContext(), packets);
    }

    public void flush() {
        super.flush(channelContext());
    }

    public Channel channel() {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Channel not connected or inactive.");
        }
        return channel;
    }

    private ChannelHandlerContext channelContext() {
        if (channel == null || !channel.isActive()) {
            return null;
        }
        return channel.pipeline().firstContext();
    }
}
