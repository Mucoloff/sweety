package dev.sweety.saas.hub.backend.handler;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.saas.hub.backend.ServiceNode;
import io.netty.channel.ChannelHandlerContext;

public class EmptyHandler extends ServiceNodeHandler {

    public EmptyHandler(ServiceNode node) {
        super(node);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public boolean handled(Packet packet) {
        return false;
    }
}
