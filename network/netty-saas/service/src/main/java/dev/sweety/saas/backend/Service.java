package dev.sweety.saas.backend;

import dev.sweety.netty.backend.Backend;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.internal.RoutingContext;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.saas.backend.monitoring.MetricsReporter;
import dev.sweety.saas.service.IService;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.saas.service.config.ServicesConfig;
import dev.sweety.saas.service.packet.global.handshake.SystemConnection;
import dev.sweety.saas.service.packet.global.handshake.SystemConnectionTransaction;
import dev.sweety.saas.service.packet.global.ping.SystemPing;
import dev.sweety.saas.service.packet.global.ping.SystemPong;
import dev.sweety.time.TimeMode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public abstract class Service extends Backend implements IService {

    protected final ServiceType type;
    protected final ServicesConfig config;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().name("metrics-reporter").factory());
    private final MetricsReporter metrics = new MetricsReporter(this);

    public Service(ServicesConfig config, ServiceType type, int id, IPacketRegistry packetRegistry) {
        super(config.hubHost(), config.hubPort(), packetRegistry, config.service(type, id).getPort());

        this.type = type;
        this.config = config;

        scheduler.scheduleAtFixedRate(() -> {
            sample(this.metrics);
            this.metrics.report();
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof InternalPacket) {
            super.onPacketReceive(ctx, packet);
            return;
        }

        final long now = Messenger.timeMode.now();
        switch (packet) {
            case SystemPing systemPing -> sendPacket(ctx, new SystemPong(now));
            // Hub drives the ping cycle (5s interval). Service only responds with Pong.
            //case SystemPong systemPong -> { /* Hub-driven — no action needed */ }
            case SystemConnectionTransaction transaction -> transactions.completeHubTransaction(transaction);
            case null, default -> {
                //todo Process service-specific packets
            }
        }
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        super.join(ctx, promise);

        transactions.sendHubTransaction(new SystemConnectionTransaction(new SystemConnection(this.type, SystemConnection.State.REQUEST)))
                .whenComplete((conn, error) -> {
                    if (error != null) {
                        backendLogger().push("handshake").error(error).pop();
                        ctx.close();
                        return;
                    }

                    if (conn == null || conn.timeout()) {
                        backendLogger().push("handshake").error("Connection handshake timed out or rejected by Hub.").pop();
                        ctx.close();
                        return;
                    }

                    if (conn.request()) {
                        backendLogger().push("handshake").error("Received unexpected handshake request from Hub.").pop();
                        ctx.close();
                        return;
                    }

                    if (conn.response()) {
                        backendLogger().info("Successfully connected and identified as " + type + " to Hub.");
                    }

                }).whenComplete((v, tr) -> {
                    backendLogger().push("handshake");
                    if (tr != null) backendLogger().error(tr);
                    else backendLogger().info("Identifying as " + type + "...");
                    backendLogger().pop();
                });
    }

    @Override
    public void handleInternal(int sender, int receiver, Packet packet, ArrayList<Packet> results) {
        if (receiver != type.id()) throw new IllegalArgumentException("Invalid receiver id.");
        final ServiceType send = ServiceType.of(sender);
        if (send == null) throw new IllegalArgumentException("Invalid sender id.");
        handleInternal(send, packet, results);
    }

    public abstract void handleInternal(final ServiceType sender, final Packet packet, final ArrayList<Packet> results);

    @Override
    public void handleInternal(final Packet packet, final ArrayList<Packet> results) {
        throw new UnsupportedOperationException("You shouldn't use this method.");
    }

    protected void sample(final MetricsReporter metrics) {

    }

    @Override
    public void leave(ChannelHandlerContext ctx) {
    }

    @Override
    public int typeId() {
        return IService.super.typeId();
    }

    /*fire-and-forget: forward without transaction*/
    public <T> CompletableFuture<T> sendFireAndForget(ServiceType receiver, Packet... packets) {
        return transactions.sendFireAndForget(typeId(), receiver.id(), null, packets);
    }

    public <T> CompletableFuture<T> sendFireAndForget(ServiceType receiver, @Nullable RoutingContext context, Packet... packets) {
        return transactions.sendFireAndForget(typeId(), receiver.id(), context, packets);
    }

    /*used for services*/
    public <T> CompletableFuture<T> sendRequest(ServiceType receiver, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return transactions.sendRequest(typeId(), receiver.id(), null, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendRequest(ServiceType receiver, @Nullable RoutingContext context, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return transactions.sendRequest(typeId(), receiver.id(), context, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendResponse(long request, ServiceType receiver, Packet... responses) {
        return transactions.sendResponse(request, typeId(), receiver.id(), responses);
    }

    /* used for services transaction packets*/
    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(ServiceType receiver, @Nullable RoutingContext context, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return transactions.sendTransactionRequest(typeId(), receiver.id(), context, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(ServiceType receiver, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return transactions.sendTransactionRequest(typeId(), receiver.id(), null, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<R, ?>> CompletableFuture<T> sendTransactionResponse(long request, ServiceType receiver, T response) {
        return transactions.sendResponse(request, typeId(), receiver.id(), response);
    }

    /*used for hub transactions*/
    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendHubTransaction(T transaction) {
        return transactions.sendHubTransaction(transaction);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendHubTransaction(T transaction, long timeoutMillis) {
        return transactions.sendHubTransaction(transaction, timeoutMillis);
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> boolean completeHubTransaction(Transaction packet) {
        return transactions.completeHubTransaction(packet);
    }

    public String typeName() {
        return type.name();
    }

    @Override
    public String convertType(int type) {
        return ServiceType.of(type).name();
    }

    @Override
    public ServiceType type() {
        return type;
    }

    public ServicesConfig config() {
        return config;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public MetricsReporter metrics() {
        return metrics;
    }
}
