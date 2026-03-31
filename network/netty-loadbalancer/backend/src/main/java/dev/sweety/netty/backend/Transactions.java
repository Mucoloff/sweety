package dev.sweety.netty.backend;

import dev.sweety.color.AnsiColor;
import dev.sweety.netty.packet.internal.ForwardData;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.internal.RoutingContext;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class Transactions {

    private final TransactionManager transactionManager;
    private final Backend backend;

    public Transactions(Backend backend) {
        this.backend = backend;
        transactionManager = new TransactionManager(backend);
    }

    public boolean completeResponse(InternalPacket internal, ChannelHandlerContext ctx) {
        return transactionManager.completeResponse(internal, ctx);
    }

    public void shutdown() {
        transactionManager.shutdown();
    }

    /*fire-and-forget: forward packet without creating a transaction*/
    public <T> CompletableFuture<T> sendFireAndForget(int sender, int receiver, @Nullable RoutingContext context, Packet... requests) {
        final ForwardData forwardData = new ForwardData(sender, receiver, context, backend.packetRegistry()::getPacketId, requests);
        final InternalPacket internal = InternalPacket.fireAndForget(forwardData);
        return backend.sendPacket(internal);
    }

    /*used for services*/
    public <T> CompletableFuture<T> sendRequest(int sender, int receiver, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return sendRequest(sender, receiver, null, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendRequest(int sender, int receiver, @Nullable RoutingContext context, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        final ForwardData forwardData = new ForwardData(sender, receiver, context, backend.packetRegistry()::getPacketId, requests);
        final InternalPacket internal = new InternalPacket(forwardData);
        return sendInternalRequest(internal, responseConsumer);
    }

    private <T> CompletableFuture<T> sendInternalRequest(InternalPacket internal, BiConsumer<Packet[], Throwable> responseConsumer) {
        transactionManager.registerRequest(internal, Backend.requestTimeout()).exceptionally(throwable -> {
                    backend.backendLogger().push("transaction", AnsiColor.RED_BRIGHT).error(internal.requestCode(), throwable.getMessage()).pop();
                    responseConsumer.accept(null, throwable);
                    return null;
                })
                .thenApply(response -> {
                    final Packet[] packets = backend.handleForward(response);
                    responseConsumer.accept(packets, null);
                    return packets;
                });

        final CompletableFuture<T> sendFuture = backend.sendPacket(internal);
        sendFuture.whenComplete((v, t) -> {
            if (t != null) {
                transactionManager.failRequest(internal.getRequestId(), t);
            }
        });
        return sendFuture;
    }

    public <T> CompletableFuture<T> sendResponse(long request, int sender, int receiver, Packet... responses) {
        final InternalPacket packet = new InternalPacket(request,
                new ForwardData(sender, receiver, backend.packetRegistry()::getPacketId, responses));
        return backend.sendPacket(packet);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(int sender, int receiver, @Nullable RoutingContext context, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {

        final SimpleLogger logger = backend.backendLogger();

        final long id = transactionRequest.getRequestId();
        final InternalPacket internal = new InternalPacket(
                id,
                new ForwardData(sender, receiver, context, backend.packetRegistry()::getPacketId, transactionRequest),
                true
        );

        return sendInternalRequest(internal, (responses, t) -> {
            logger.push("transaction", AnsiColor.RED_BRIGHT);
            if (t != null || responses == null || responses.length == 0) {
                logger.error("Transaction failed or timed out: " + (t != null ? t.getMessage() : "no response"));
                responseConsumer.accept(null, t != null ? t : new IllegalStateException("Missing transaction response"));
                logger.pop();
                return;
            }
            if (responses.length != 1) {
                logger.error("Invalid transaction: received", responses.length, "responses").pop();
                responseConsumer.accept(null, new IllegalStateException("Invalid transaction response: expected 1 response, got " + responses.length));
                return;
            }
            try {
                //noinspection unchecked
                final T transaction = (T) responses[0];
                final R response = transaction.getResponse();
                responseConsumer.accept(response, null);
                logger.pop();
            } catch (ClassCastException e) {
                logger.error("Invalid transaction response type", e).pop();
            }

        });
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(int sender, int receiver, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {
        return sendTransactionRequest(sender, receiver, null, responseConsumer, transactionRequest);
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<R, ?>> CompletableFuture<T> sendTransactionResponse(long request, int sender, int receiver, T response) {
        return sendResponse(request, sender, receiver, response);
    }

    /*used for hub transactions*/
    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendHubTransaction(T transaction) {
        return this.sendHubTransaction(transaction, Backend.requestTimeout());
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendHubTransaction(T transaction, long timeoutMillis) {
        final ChannelHandlerContext ctx = backend.channelContext();
        if (ctx == null) return CompletableFuture.completedFuture(null);
        return transactionManager.sendTransaction(ctx, transaction, timeoutMillis);
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> boolean completeHubTransaction(Transaction packet) {
        final ChannelHandlerContext ctx = backend.channelContext();
        if (ctx == null) return false;

        return transactionManager.completeResponse(packet, ctx);
    }

}
