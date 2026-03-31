package dev.sweety.saas.backend.handler;

import dev.sweety.netty.packet.internal.RoutingContext;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.backend.Service;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.util.logger.SimpleLogger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public abstract class LocalHandler {

    protected final Service service;

    protected final SimpleLogger logger;

    public LocalHandler(Service service) {
        this(service, "");
    }

    public LocalHandler(Service service, String handlerName) {
        this.service = service;
        String name = handlerName.isEmpty() ? "" : "/" + handlerName;
        this.logger = new SimpleLogger(service.type().name() + name);
    }

    protected Service service() {
        return this.service;
    }

    public abstract void handle(final Packet packet, final ArrayList<Packet> results);

    /*used for services*/
    public <T> CompletableFuture<T> sendRequest(ServiceType receiver, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return this.service.sendRequest(receiver, null, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendRequest(ServiceType receiver, @Nullable RoutingContext context, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return this.service.sendRequest( receiver, context, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendResponse(long request, ServiceType receiver, Packet... responses) {
        return this.service.sendResponse(request,  receiver, responses);
    }

    /* used for services transaction packets*/
    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(ServiceType receiver, @Nullable RoutingContext context, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return this.service.sendTransactionRequest( receiver, context, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(ServiceType receiver, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return this.service.sendTransactionRequest( receiver, null, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<R, ?>> CompletableFuture<T> sendTransactionResponse(long request, ServiceType receiver, T response) {
        return this.service.sendResponse(request,  receiver, response);
    }

    /*used for hub transactions*/
    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendHubTransaction(T transaction) {
        return this.service.sendHubTransaction(transaction);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendHubTransaction(T transaction, long timeoutMillis) {
        return this.service.sendHubTransaction(transaction, timeoutMillis);
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> boolean completeHubTransaction(Transaction packet) {
        return this.service.completeHubTransaction(packet);
    }

}