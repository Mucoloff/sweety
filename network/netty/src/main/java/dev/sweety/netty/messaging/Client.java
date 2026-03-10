package dev.sweety.netty.messaging;

import dev.sweety.core.math.vector.list.BlockingDeque;
import dev.sweety.netty.feature.QueueContext;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public abstract class Client extends Messenger<Bootstrap> {

    private final BlockingDeque<QueueContext<?>> pendingPackets = new BlockingDeque<>();

    protected final int localPort;

    public Client(String host, int port, IPacketRegistry packetRegistry, int localPort) {
        super(new Bootstrap(), host, port, packetRegistry, localPort);
        this.localPort = localPort;
    }

    public <T> CompletableFuture<T> sendPacket(Packet packet) {
        return super.sendPacket(channelContext(), packet);
    }

    public <T> CompletableFuture<T> sendPacket(Packet... packets) {
        return super.sendPacket(channelContext(), packets);
    }

    public <T> CompletableFuture<T> writePacket(Packet packet) {
        return writePacket(channelContext(), packet);
    }

    public <T> CompletableFuture<T> writePacket(Packet... packets) {
        return writePacket(channelContext(), packets);
    }

    @Override
    public <T> CompletableFuture<T> writePacket(ChannelHandlerContext ctx, Packet packet) {
        if (ctx == null || !ctx.channel().isActive()) return enqueue(packet);
        return super.writePacket(ctx, packet);
    }

    @Override
    public <T> CompletableFuture<T> writePacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (ctx == null || !ctx.channel().isActive()) return enqueue(msgs);
        return super.writePacket(ctx, msgs);
    }

    public void flush() {
        super.flush(channelContext());
    }

    public Channel channel() {
        if (isActive()) return channel;
        throw new IllegalStateException("Channel not connected or inactive.");
    }

    public boolean isActive() {
        return this.channel != null && this.channel.isActive();
    }

    public @Nullable ChannelHandlerContext channelContext() {
        if (isActive()) return channel.pipeline().firstContext();
        return null;
    }

    @Override
    public CompletableFuture<Channel> connect() {
        return super.connect().whenComplete((c, t) -> {
            if (c != null) drainPending();
        });
    }

    private <T> CompletableFuture<T> enqueue(Packet... packets) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        pendingPackets.enqueue(new QueueContext<>(future, packets));
        return future;
    }

    private void drainPending() {
        while (!pendingPackets.isEmpty()) {
            final QueueContext<?> context = pendingPackets.dequeue();
            final Packet[] packets = context.packets();
            //noinspection unchecked
            final CompletableFuture<Object> future = (CompletableFuture<Object>) context.future();

            sendPacket(packets).whenComplete((v, t) -> {
                if (t != null) future.completeExceptionally(t);
                else future.complete(v);
            });
        }
    }

}