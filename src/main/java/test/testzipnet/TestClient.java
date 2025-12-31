package test.testzipnet;

import dev.sweety.core.event.info.State;
import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.impl.file.FilePacket;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.messaging.Client;
import dev.sweety.network.cloud.packet.TransactionManager;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.model.PacketTransaction;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import dev.sweety.network.cloud.packet.registry.OptimizedPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import test.testzipnet.ping.PingTransaction;

import java.util.function.BiConsumer;

public class TestClient extends Client {

    EcstacyLogger logger = new EcstacyLogger("Client").fallback();

    TransactionManager transactionManager = new TransactionManager();

    public TestClient(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof TextPacket text) {
            logger.info("messaggio: " + text.getText());
        } else if (packet instanceof PingTransaction transaction) {
            if (transaction.hasResponse()) {
                logger.info("Ricevuto pong con ID: " + transaction.getRequestId());
                boolean b = transactionManager.completeResponse(transaction.getRequestId(), transaction.getResponse());
                logger.info("passed: " + b);
            }
        }
    }

    @Override
    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, State state) {
        logger.info("sending: " + packet.getClass().getSimpleName() + " - " + state);
    }

    @Override
    public void stop() {
        transactionManager.shutdown();
        super.stop();
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

    public static void main(String[] args) throws Throwable {

        IPacketRegistry packetRegistry = new OptimizedPacketRegistry(TextPacket.class, FilePacket.class, PingTransaction.class);

        TestClient client = new TestClient("localhost", 8080, packetRegistry);
        client.start();

        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));

        StringBuilder buff = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            buff.append("Questo è un messaggio di test numero ").append(i).append(". ");
        }

        client.sendPacket(new TextPacket("Ciao dal client!"));
        //client.sendPacket(new TextPacket(buff.toString()));

        PingTransaction ping = new PingTransaction(new PingTransaction.Ping());

        BiConsumer<PacketTransaction.Transaction, Throwable> completedTransaction = (PacketTransaction.Transaction t, Throwable ex) -> {
            client.logger.info("completed transaction");
            if (t != null) client.logger.info("received transaction " + t.getClass().getSimpleName());
            if (ex != null) client.logger.warn(ex);
        };

        client.transactionManager.registerRequest(ping.getRequestId(), 10000L).whenComplete(completedTransaction);
        //client.transactionManager.registerRequest(-1L, 1000L).whenComplete(completedTransaction);

        client.writePacket(ping);
        client.flush();


        while (true) {
            Thread.onSpinWait();
        }
    }

}
