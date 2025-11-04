package test.testzipnet;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.impl.file.FilePacket;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.messaging.Server;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import dev.sweety.network.cloud.packet.registry.OptimizedPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class TestServer extends Server {

    EcstacyLogger logger = new EcstacyLogger("Server").fallback();

    public TestServer(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {

        if (packet instanceof TextPacket text) {
            logger.info("messaggio: " + text.getText());
        }
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Exception: ", throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Client connesso: ", ctx.channel().remoteAddress());
        super.addClient(ctx, ctx.channel().remoteAddress());
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Client disconnesso: ", ctx.channel().remoteAddress());
        super.removeClient(ctx.channel().remoteAddress());
    }

    public static void main(String[] args) throws Throwable {

        IPacketRegistry packetRegistry = new OptimizedPacketRegistry(TextPacket.class, FilePacket.class);

        TestServer server = new TestServer("localhost", 8080, packetRegistry);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        while (true) {
            Thread.onSpinWait();
        }
    }
}
