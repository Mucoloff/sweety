package dev.sweety.project.netty.loadbalancer.impl;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.backend.Backend;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.project.netty.packet.text.TextPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class BackendTest extends Backend {

    private final SimpleLogger logger = new SimpleLogger(BackendTest.class);

    public BackendTest(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
    }

    @Override
    public Packet[] handlePackets(Packet packet) {
        String text = (packet instanceof TextPacket t) ? t.getText() : "Unknown Packet Type";
        logger.info("Contenuto:", text);

        return new Packet[]{
                new TextPacket(port + "/" + text)
        };
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect", AnsiColor.GREEN_BRIGHT).info(ctx.channel().localAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void leave(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.logger.push("disconnect",AnsiColor.RED_BRIGHT).info(ctx.channel().localAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

}
