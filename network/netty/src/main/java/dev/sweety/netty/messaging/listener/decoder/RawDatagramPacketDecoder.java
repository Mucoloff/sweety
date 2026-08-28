package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.netty.messaging.transport.RawDatagramPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * {@code UdpTransport.raw()}'s only inbound stage: wraps every datagram in a {@link RawDatagramPacket}
 * and hands it downstream to {@code NettyWatcher}, which dispatches it through the same
 * {@code Messenger#onPacketReceive} path TCP already uses — no separate raw-datagram hook.
 */
public class RawDatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
        ByteBuf content = msg.content();
        byte[] data = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), data);
        out.add(new RawDatagramPacket(data, msg.sender()));
    }
}
