package test.loadbalancer;// File: DummyClient.java

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.messaging.Client;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Un client di esempio che si connette al Load Balancer e invia pacchetti.
 */
public class DummyClient extends Client {

    EcstacyLogger logger = new EcstacyLogger("Client").fallback();

    public DummyClient(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof TextPacket text)
            logger.info("messaggio: " + text.getText());
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore nel client: ", throwable);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Client connesso al Load Balancer!");
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Client disconnesso dal Load Balancer.");
        promise.setSuccess();
    }
}
