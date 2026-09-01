package dev.sweety.netty.common.backend;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

public interface IBackend {

    int typeId();

    String host();

    int port();

    void onPacketReceive(final ChannelHandlerContext ctx, final Packet packet);
}
