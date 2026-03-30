package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.color.AnsiColor;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.loadbalancer.common.packet.internal.ForwardData;
import dev.sweety.netty.loadbalancer.common.packet.internal.InternalPacket;
import dev.sweety.netty.loadbalancer.common.packet.internal.RoutingContext;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.model.PacketTransaction;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    /*used for services*/
    public <T> CompletableFuture<T> sendRequest(int sender, int receiver, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        return sendRequest(sender, receiver, null, responseConsumer, requests);
    }

    public <T> CompletableFuture<T> sendRequest(int sender, int receiver, @Nullable RoutingContext context, BiConsumer<Packet[], Throwable> responseConsumer, Packet... requests) {
        backend.backendLogger().push("sendRequest", AnsiColor.YELLOW).info("Sending request to " + backend.convertType(receiver)).pop();
        final InternalPacket internal = new InternalPacket(new ForwardData(sender, receiver, context, backend.packetRegistry()::getPacketId, requests));

        transactionManager.registerRequest(internal, Backend.requestTimeout()).exceptionally(throwable -> {
                    backend.backendLogger().push("transaction", AnsiColor.RED_BRIGHT).error(internal.requestCode(), throwable.getMessage());
                    responseConsumer.accept(null, throwable);
                    return null;
                })
                .thenApply(response -> {
                    backend.backendLogger().push("transaction", AnsiColor.GREEN_BRIGHT).info(internal.requestCode());
                    final Packet[] packets = backend.handleForward(response);
                    responseConsumer.accept(packets, null);
                    backend.backendLogger().info("Handled " + packets.length + " packets").pop();
                    return packets;
                });

        return backend.sendPacket(internal);
    }

    public <T> CompletableFuture<T> sendResponse(long request, int sender, int receiver, Packet... responses) {
        backend.backendLogger().push("sendResponse", AnsiColor.YELLOW).info("Sending response from", backend.convertType(sender), "to", backend.convertType(receiver)).pop();
        return backend.sendPacket(new InternalPacket(request, new ForwardData(sender, receiver, backend.packetRegistry()::getPacketId, responses)));
    }

    /* used for services transaction packets*/
    private final Map<Long, State> responseTracker = new ConcurrentHashMap<>();

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransactionRequest(int sender, int receiver, @Nullable RoutingContext context, BiConsumer<R, Throwable> responseConsumer, T transactionRequest) {

        final SimpleLogger logger = backend.backendLogger();

        logger.push("transactionRequest", AnsiColor.YELLOW).info("Sending transaction request to " + backend.convertType(receiver)).pop();

        final long id = transactionRequest.getRequestId();

        this.responseTracker.put(id, State.PENDING);

        final CompletableFuture<R> sentFuture = sendRequest(sender, receiver, context, (responses, t) -> {
            logger.push("transaction", AnsiColor.RED_BRIGHT);
            if (responses.length != 1) {
                logger.error("Invalid transaction: received", responses.length, "responses").pop();
                responseConsumer.accept(null, t);
                return;
            }
            try {
                //noinspection unchecked
                final T transaction = (T) responses[0];

                final State state = responseTracker.containsKey(transaction.getRequestId()) ? responseTracker.remove(transaction.getRequestId()) : null;
                switch (state) {
                    case PENDING -> logger.error("Transaction pending");
                    case EXPIRED -> logger.error("Transaction expired");
                    case ERROR -> logger.error("Transaction error");
                    case SENT -> {
                        logger.push("success", AnsiColor.GREEN_BRIGHT).info("Transaction completed successfully").pop();
                        final R response = transaction.getResponse();
                        responseConsumer.accept(response, null);
                    }
                    case null, default -> logger.error("Unknown transaction state").pop();
                }
                logger.pop();
            } catch (ClassCastException e) {
                logger.error("Invalid transaction response type", e).pop();
            }

        }, transactionRequest);

        sentFuture.whenComplete((o, t) -> {
            if (t != null) responseTracker.put(id, State.ERROR);
            else responseTracker.put(id, State.SENT);
        });

        return sentFuture;
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

    public enum State {
        PENDING, SENT, EXPIRED, ERROR
    }

}
