package dev.sweety.project.netty.lb;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.v2.Backend;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.project.netty.packet.text.TextPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class BackendTest extends Backend {

    private final SimpleLogger logger = new SimpleLogger(BackendTest.class);

    public BackendTest(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        logger.push("" + port);
    }

    @Override
    public Packet[] handlePackets(Packet[] packets) {
        logger.push("handlePackets").info("packets received: " + packets.length).pop();

        for (int i = 0; i < packets.length; i++) {
            String text = (packets[i] instanceof TextPacket packet) ? packet.getText() : "Unknown Packet Type";
            logger.info("Contenuto:", text);
            packets[i] = new TextPacket(port + "/" + text);
        }

        return packets;
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("Join").info(ctx.channel().remoteAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.logger.push("quit").info(ctx.channel().remoteAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

}
