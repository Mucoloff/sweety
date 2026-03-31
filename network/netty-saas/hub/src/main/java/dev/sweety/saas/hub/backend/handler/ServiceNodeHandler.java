package dev.sweety.saas.hub.backend.handler;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.saas.hub.backend.ServiceNode;
import io.netty.channel.ChannelHandlerContext;

public abstract class ServiceNodeHandler {

    private final ServiceNode node;

    protected ServiceNodeHandler(final ServiceNode node) {
        this.node = node;
    }

    public abstract void handle(final ChannelHandlerContext ctx, final Packet packet);
    public abstract boolean handled(Packet packet);

    public ServiceNode node() {
        return node;
    }

}
