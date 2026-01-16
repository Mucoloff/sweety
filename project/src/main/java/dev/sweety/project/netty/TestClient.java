package dev.sweety.project.netty;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.event.Event;
import dev.sweety.event.EventSystem;
import dev.sweety.event.interfaces.LinkEvent;
import dev.sweety.event.interfaces.Listener;
import dev.sweety.event.processor.EventMapping;
import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.BatchPacket;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.file.FilePacket;
import dev.sweety.project.netty.packet.text.TextPacket;
import dev.sweety.project.netty.packet.text.event.TextPacketEvent;
import dev.sweety.project.netty.ping.PingTransaction;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class TestClient extends Client {

    private final SimpleLogger logger = new SimpleLogger("Client");
    private final TransactionManager transactionManager = new TransactionManager(this);
    private final EventSystem eventSystem = new EventSystem();
    private final EventMapping eventMapping = new EventMapping(eventSystem);

    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);


    public TestClient(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        eventSystem.subscribe(new Object() {

            @LinkEvent
            Listener<TextPacketEvent> onText = event -> logger.info("Evento TextPacketEvent ricevuto con testo: " + event.getText());

        });

        sendPacket(new TextPacket("Ciao dal client!"));

        PingTransaction ping = new PingTransaction(new PingTransaction.Ping("ping da client"));

        BiConsumer<PacketTransaction.Transaction, Throwable> completedTransaction = (PacketTransaction.Transaction response, Throwable ex) -> {
            if (response != null) {
                logger.info("completed transaction " + response.getClass().getSimpleName());

                if (response instanceof PingTransaction.Pong pong) {
                    logger.info("\treceived pong: " + pong.getText());
                }

            }
            if (ex != null) logger.warn(ex);
        };

        setOnConnect(c -> {
            transactionManager.sendTransaction(c.pipeline().firstContext(), ping, 10000L).whenComplete(completedTransaction);
            sendPacket(new TextPacket("ciao"));

            PingTransaction p2 = new PingTransaction(new PingTransaction.Ping("ping in batch from client"));

            sendPacket(new BatchPacket(getPacketRegistry()::getPacketId,
                    new TextPacket("aaa"),
                    new TextPacket("bbb"),
                    p2
            ));

            transactionManager.registerRequest(p2, 10000L).whenComplete((pong, ex) -> System.out.println("Received response in batch:")).whenComplete(completedTransaction);
        });

    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof TextPacket text) {
            logger.info("messaggio: " + text.getText());
        } else if (packet instanceof PingTransaction transaction) {
            if (transaction.hasResponse()) {
                logger.info("Ricevuto pong con ID: " + transaction.requestCode());
                boolean b = transactionManager.completeResponse(transaction.getRequestId(), ctx, transaction.getResponse());
                logger.info("passed: " + b);
            }
        }

        eventMapping.dispatch(packet);
    }

    @Override
    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
        logger.info("sending: " + packet.getClass().getSimpleName() + " - " + (pre ? "pre" : "post"));

        if (!pre) return;

        if (packet instanceof TextPacket t && false) {
            PacketBuffer b = t.buffer();
            b.writeString("[messaggio editato] " + b.readString());
        }

    }


    @Override
    public CompletableFuture<Channel> connect() {
        return super.connect().exceptionally(t -> {
            this.autoReconnect.onException(t);
            return null;
        });
    }

    @Override
    public void stop() {
        transactionManager.shutdown();
        autoReconnect.shutdown();
        super.stop();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore nel client: ", throwable);
        autoReconnect.onException(throwable);
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
        autoReconnect.onQuit();
    }

    public static void main(String[] args) throws Throwable {

        IPacketRegistry packetRegistry = new OptimizedPacketRegistry(TextPacket.class, FilePacket.class, PingTransaction.class, BatchPacket.class);

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


        while (true) {
            Thread.onSpinWait();
        }
    }

}
