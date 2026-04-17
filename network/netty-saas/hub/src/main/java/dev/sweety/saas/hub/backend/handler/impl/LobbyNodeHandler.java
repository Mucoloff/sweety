package dev.sweety.saas.hub.backend.handler.impl;

import ac.ecstacy.hub.backend.ServiceNode;
import ac.ecstacy.hub.backend.handler.ServiceNodeHandler;
import ac.ecstacy.netty.api.packet.model.Packet;
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
