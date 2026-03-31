package dev.sweety.netty.packet.queue;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class PacketReorder {

    private final Map<SocketAddress, OrderedResponseQueue> clientQueues = new ConcurrentHashMap<>();

    public OrderedResponseQueue enqueue(ChannelHandlerContext ctx, BiConsumer<ChannelHandlerContext, Packet[]> sender) {
        return clientQueues.computeIfAbsent(ctx.channel().remoteAddress(), addr -> new OrderedResponseQueue(ctx, sender));
    }

    public OrderedResponseQueue find(SocketAddress socketAddress) {
        return clientQueues.get(socketAddress);
    }

    public OrderedResponseQueue remove(SocketAddress addr) {
        final OrderedResponseQueue queue = clientQueues.remove(addr);
        if (queue != null) queue.reset();
        return queue;
    }

    public void shutdown() {
        clientQueues.values().forEach(OrderedResponseQueue::reset);
    }
}
