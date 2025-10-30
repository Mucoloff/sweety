package test.loadbalancer;// File: DummyBackend.java

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.impl.PacketRegistry;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.loadbalancer.BackendServer;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Un semplice server di backend che estende la tua classe Server.
 * Stampa un messaggio quando riceve un pacchetto.
 */
public class DummyBackend extends BackendServer {

    final EcstacyLogger logger;

    public DummyBackend(String host, int port) {
        super(host, port);
        this.logger = new EcstacyLogger("ServerBackend - " + port).fallback();
    }

    @Override
    public PacketOut[] handlePackets(ChannelHandlerContext ctx, PacketIn packet) {
        logger.info(String.format("Pacchetto ricevuto! ID: %d, Timestamp: %d", packet.getId(), packet.getTimestamp()));

        if (packet.getId() == PacketRegistry.TEXT.id()) {
            String originalText = new TextPacket.In(packet).getText();
            logger.info("Contenuto:", originalText);

            // Esempio di risposta multipla
            return new PacketOut[]{
                    new TextPacket.Out("Prima parte della risposta."),
                    new TextPacket.Out("Seconda e ultima parte per: " + originalText)
            };
        }

        // Nessuna risposta per altri tipi di pacchetti
        return new PacketOut[0];
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
}
