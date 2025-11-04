package dev.sweety.network.cloud.impl.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

public record PacketQueue(Packet packet, ChannelHandlerContext ctx) {

}
