package dev.sweety.netty.feature;

import dev.sweety.core.thread.ProfileThread;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.PacketTransaction;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.*;

public class TransactionManager {

    private final Map<Long, CompletableFuture<PacketTransaction.Transaction>> pending = new ConcurrentHashMap<>();

    private final ProfileThread scheduler = new ProfileThread("transaction-manager-thread");

    private final Messenger<? extends AbstractBootstrap<?, ?>> messenger;

    public TransactionManager(Messenger<? extends AbstractBootstrap<?, ?>> messenger) {
        this.messenger = messenger;
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> CompletableFuture<Response> registerRequest(Transaction packet, long timeoutMillis) {
        return registerRequest(packet.getRequestId(), timeoutMillis);
    }

    public <Response extends PacketTransaction.Transaction> CompletableFuture<Response> registerRequest(long requestId, long timeoutMillis) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        //noinspection unchecked
        CompletableFuture<PacketTransaction.Transaction> prev = pending.putIfAbsent(requestId, (CompletableFuture<PacketTransaction.Transaction>) future);
        if (prev != null) {
            future.completeExceptionally(new IllegalStateException("Request id already registered: " + Long.toHexString(requestId)));
            return future;
        }

        CompletableFuture<?> timeoutHandle = scheduler.schedule(() -> {
            CompletableFuture<PacketTransaction.Transaction> f = pending.remove(requestId);
            if (f != null && !f.isDone()) {
                TimeoutException ex = new TimeoutException("Transaction timed out: " + Long.toHexString(requestId));
                ex.setStackTrace(new StackTraceElement[0]);
                f.completeExceptionally(ex);
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        future.whenComplete((response, ex) -> {
            pending.remove(requestId);
            timeoutHandle.cancel(false);
        });

        return future;
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> boolean completeResponse(Transaction packet, ChannelHandlerContext ctx) {
        return packet.hasResponse() && completeResponse(packet.getRequestId(), ctx, packet.getResponse());
    }

    public <Response extends PacketTransaction.Transaction> boolean completeResponse(long id, ChannelHandlerContext ctx, Response response) {
        //noinspection unchecked
        CompletableFuture<Response> future = (CompletableFuture<Response>) pending.remove(id);
        if (future == null) return false;

        Messenger.safeExecute(ctx, () -> future.complete(response));
        return true;
    }

    public void shutdown() {
        scheduler.shutdown();
        pending.forEach((k, f) -> f.completeExceptionally(new CancellationException("Manager shutdown")));
        pending.clear();
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>>
    CompletableFuture<R> sendTransaction(ChannelHandlerContext ctx, T transaction, long timeoutMillis) {

        CompletableFuture<R> result = new CompletableFuture<>();

        Messenger.safeExecute(ctx, () -> messenger.sendPacket(ctx, transaction).whenComplete((v, ex) -> {
            if (ex != null) {
                result.completeExceptionally(ex);
                return;
            }

            registerRequest(transaction, timeoutMillis)
                    .whenComplete((resp, rex) -> {
                        if (rex != null) result.completeExceptionally(rex);
                        else result.complete(resp);
                    });
        }));

        return result;
    }

}
