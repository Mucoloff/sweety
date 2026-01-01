package dev.sweety.loadbalancer;// File: DummyBackend.java

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.packet.text.TextPacket;
import dev.sweety.network.cloud.loadbalancer.backend.BackendServer;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.UUID;

/**
 * Un semplice server di backend che estende la tua classe Server.
 * Stampa un messaggio quando riceve un pacchetto.
 */
public class DummyBackend extends BackendServer {

    final EcstacyLogger logger;

    public DummyBackend(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.logger = new EcstacyLogger("ServerBackend - " + port).fallback();
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.info("received packet", packet);
        super.onPacketReceive(ctx, packet);
    }

    @Override
    public Packet[] handlePackets(ChannelHandlerContext ctx, Packet packet) {
        logger.info(String.format("Pacchetto ricevuto! ID: %d, Timestamp: %d", packet.id(), packet.timestamp()));
        logger.info(packet);

        if (packet instanceof TextPacket text) {
            String originalText = text.getText();
            logger.info("Contenuto:", originalText);

            // Esempio di risposta multipla
            return new Packet[]{
                    new TextPacket("Prima parte della risposta."),
                    new TextPacket("Seconda e ultima parte per: " + originalText)
            };
        }

        if (packet instanceof PlayerPacket playerPacket) {
            UUID id = playerPacket.getUuid();
            String text = playerPacket.getText();

            logger.info("player ", id, ":", text);

        }

        // Nessuna risposta per altri tipi di pacchetti
        return new Packet[0];
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Exception: ", throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Backend connesso: ", ctx.channel().remoteAddress());
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Backend disconnesso: ", ctx.channel().remoteAddress());
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }
}
