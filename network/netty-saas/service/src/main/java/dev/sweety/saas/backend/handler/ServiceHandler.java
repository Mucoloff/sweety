package dev.sweety.saas.backend.handler;

import dev.sweety.netty.packet.internal.RoutingContext;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.backend.Service;
import dev.sweety.saas.service.ServiceType;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public abstract class ServiceHandler extends LocalHandler {

    protected final ServiceType receiver;

    public ServiceHandler(Service service, ServiceType receiver, String handlerName) {
        super(service, handlerName);
        this.receiver = receiver;
    }

    public ServiceHandler(final Service service, final ServiceType receiver) {
        super(service, receiver.name());
        this.receiver = receiver;
    }

    /*fire-and-forget: forward without transaction*/
    public <T> CompletableFuture<T> sendFireAndForget(Packet... packets) {
        return this.service.sendFireAndForget(receiver, packets);
    }

    public <T> CompletableFuture<T> sendFireAndForget(@Nullable RoutingContext context, Packet... packets) {
        return this.service.sendFireAndForget(receiver, context, packets);
    }

    /*used for services*/
    public <T> CompletableFuture<T> sendRequest(BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return this.service.sendRequest(receiver, null, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendRequest(@Nullable RoutingContext context, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return this.service.sendRequest(receiver, context, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendResponse(long request, Packet... responses) {
        return this.service.sendResponse(request, receiver, responses);
    }

    /* used for services transaction packets*/
    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(@Nullable RoutingContext context, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return this.service.sendTransactionRequest(receiver, context, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return this.service.sendTransactionRequest(receiver, null, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<R, ?>> CompletableFuture<T> sendTransactionResponse(long request, T response) {
        return this.service.sendResponse(request, receiver, response);
    }

}
