package dev.sweety.network.cloud.loadbalancer.packet;

import dev.sweety.network.cloud.packet.incoming.PacketIn;
import io.netty.channel.ChannelHandlerContext;

public record PacketQueue(PacketIn packet, ChannelHandlerContext ctx) {

}
