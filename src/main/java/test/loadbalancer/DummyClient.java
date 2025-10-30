package test.loadbalancer;// File: DummyClient.java

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.impl.PacketRegistry;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.messaging.Client;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Un client di esempio che si connette al Load Balancer e invia pacchetti.
 */
public class DummyClient extends Client {

    EcstacyLogger logger = new EcstacyLogger("Client").fallback();

    public DummyClient(String host, int port) {
        super(host, port);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, PacketIn packet) {
        if (packet.getId() == PacketRegistry.TEXT.id()){
            TextPacket.In text = new TextPacket.In(packet);
            logger.info("messaggio: " + text.getText());
        }

    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore nel client: ", throwable.getMessage());
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Client connesso al Load Balancer!");
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Client disconnesso dal Load Balancer.");
    }
}
