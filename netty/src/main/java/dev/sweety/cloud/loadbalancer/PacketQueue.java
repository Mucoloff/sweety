package dev.sweety.cloud.loadbalancer;

import dev.sweety.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

public record PacketQueue(Packet packet, ChannelHandlerContext ctx) {

}
