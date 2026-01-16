package dev.sweety.project.netty;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.packet.model.BatchPacket;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.file.FilePacket;
import dev.sweety.project.netty.packet.text.TextPacket;
import dev.sweety.project.netty.ping.PingTransaction;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class TestServer extends Server {

    final SimpleLogger logger = new SimpleLogger("Server");
    final TriFunction<Packet, Integer, Long, byte[]> constructor;

    public TestServer(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this.logger);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.push("receive");
        logger.push(packet.name());

        switch (packet) {
            case TextPacket text -> {
                logger.info("messaggio: " + text.getText());
                sendPacket(ctx, new TextPacket("Ricevuto il tuo messaggio: " + text.getText()));
            }
            case PingTransaction transaction -> {
                if (transaction.hasRequest()) {
                    logger.info("Ricevuto ping con ID: " + transaction.requestCode());
                    logger.info("Contenuto del messaggio: " + transaction.getRequest().getText());
                    sendPacket(ctx, new PingTransaction(transaction.getRequestId(), new PingTransaction.Pong("pong da server")));
                }
            }
            case BatchPacket batch -> {
                String a = logger.popProfile();
                String b = logger.popProfile();
                logger.push("batch");
                for (Packet p : batch.decode(constructor)) {
                    onPacketReceive(ctx, p);
                }
                logger.pop();
                logger.push(b);
                logger.push(a);
            }
            default -> {
            }
        }


        logger.pop();
        logger.info("Processed packet: " + packet.name());
        logger.pop();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Exception: ", throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect", AnsiColor.GREEN_BRIGHT).info(ctx.channel().remoteAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("disconnect", AnsiColor.RED_BRIGHT).info(ctx.channel().remoteAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    public static void main(String[] args) throws Throwable {

        IPacketRegistry packetRegistry = new OptimizedPacketRegistry(TextPacket.class, FilePacket.class, PingTransaction.class, BatchPacket.class);

        TestServer server = new TestServer("localhost", 8080, packetRegistry);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        while (true) {
            Thread.onSpinWait();
        }
    }
}
