package dev.sweety.project.netty;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.event.Event;
import dev.sweety.event.EventSystem;
import dev.sweety.event.processor.EventMapping;
import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.packet.TransactionManager;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.file.FilePacket;
import dev.sweety.project.netty.packet.text.TextPacket;
import dev.sweety.project.netty.ping.PingTransaction;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.BiConsumer;

public class TestClient extends Client {

    SimpleLogger logger = new SimpleLogger("Client").fallback();

    private final TransactionManager transactionManager = new TransactionManager();
    private final EventSystem eventSystem = new EventSystem();
    private final EventMapping eventMapping = new EventMapping(eventSystem);

    public TestClient(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);

        eventSystem.subscribe(new Object() {

            /*@LinkEvent
            Listener<TextPacketEvent> onText = event -> logger.info("Evento TextPacketEvent ricevuto con testo: " + event.getText());
            todo
            */
        });

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

        eventMapping.dispatch(packet);
    }

    @Override
    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
        logger.info("sending: " + packet.getClass().getSimpleName() + " - " + (pre ? "pre" : "post"));

        if (!pre) return;

        if (packet instanceof TextPacket t) {
            PacketBuffer b = t.buffer();
            b.writeString("[messaggio editato]" + b.readString());
        }

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

        for (Class<? extends Packet> packetClass : packetRegistry.packets()) {
            if (!packetClass.isAnnotationPresent(GenerateEvent.class)) continue;
            //noinspection unchecked
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(packetClass.getPackageName() + "." + "event" + "." + packetClass.getSimpleName() + "Event");
            System.out.println("Registered event class: " + eventClass.getSimpleName());

            client.eventMapping.registerEventMapping(eventClass, packetClass);
        }

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
