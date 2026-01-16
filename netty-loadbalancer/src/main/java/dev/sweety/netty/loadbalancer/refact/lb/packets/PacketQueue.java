package dev.sweety.netty.loadbalancer.refact.lb.packets;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

public record PacketQueue(Packet packet, ChannelHandlerContext ctx) {
}