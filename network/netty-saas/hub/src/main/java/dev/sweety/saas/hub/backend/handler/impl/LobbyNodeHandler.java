package dev.sweety.saas.hub.backend.handler.impl;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.saas.hub.backend.ServiceNode;
import dev.sweety.saas.hub.backend.handler.ServiceNodeHandler;
import io.netty.channel.ChannelHandlerContext;

public class LobbyNodeHandler extends ServiceNodeHandler {

    public LobbyNodeHandler(ServiceNode node) {
        super(node);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Packet packet) {
        //todo handle specific hub transactions
    }

    @Override
    public boolean handled(Packet packet) {
        return false;
    }
}
