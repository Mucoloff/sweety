package test.testzipnet;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.impl.file.FilePacket;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.messaging.Client;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import dev.sweety.network.cloud.packet.registry.OptimizedPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class TestClient extends Client {

    EcstacyLogger logger = new EcstacyLogger("Client").fallback();

    public TestClient(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore nel client: ", throwable);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Client connesso al Server!");
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Client disconnesso dal Server.");
        promise.setSuccess();
    }

    public static void main(String[] args) throws Throwable{


        IPacketRegistry packetRegistry = new OptimizedPacketRegistry(TextPacket.class, FilePacket.class);



        TestClient client = new TestClient("localhost", 8080, packetRegistry);
        client.start();

        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));

        StringBuilder buff = new StringBuilder();

        for (int i = 0; i < 1000; i++) {
            buff.append("Questo è un messaggio di test numero ").append(i).append(". ");
        }

        client.sendPacket(new TextPacket("Ciao dal client!"));
        client.sendPacket(new TextPacket(buff.toString()));

        while (true) {
            Thread.onSpinWait();
        }
    }

}
