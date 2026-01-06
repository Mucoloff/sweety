package dev.sweety.netty.feature;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.PacketTransaction;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.*;

public class TransactionManager {

    private final Map<Long, CompletableFuture<PacketTransaction.Transaction>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Messenger<Bootstrap> messenger;

    public TransactionManager(Messenger<Bootstrap> messenger) {
        this.messenger = messenger;
    }

    public <T extends PacketTransaction.Transaction> CompletableFuture<T> registerRequest(long requestId, long timeoutMillis) {
        CompletableFuture<T> future = new CompletableFuture<>();

        //noinspection unchecked
        CompletableFuture<PacketTransaction.Transaction> prev = pending.putIfAbsent(requestId, (CompletableFuture<PacketTransaction.Transaction>) future);
        if (prev != null) {
            future.completeExceptionally(new IllegalStateException("Request id already registered: " + Long.toHexString(requestId)));
            return future;
        }

        ScheduledFuture<?> timeoutHandle = scheduler.schedule(() -> {
            CompletableFuture<PacketTransaction.Transaction> f = pending.remove(requestId);
            if (f != null && !f.isDone()) {
                TimeoutException ex = new TimeoutException("Transaction timed out: " + Long.toHexString(requestId));
                ex.setStackTrace(new StackTraceElement[0]);
                f.completeExceptionally(ex);
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        future.whenComplete((r, ex) -> {
            pending.remove(requestId);
            timeoutHandle.cancel(false);
        });

        return future;
    }

    public boolean completeResponse(long id, PacketTransaction.Transaction response) {
        CompletableFuture<PacketTransaction.Transaction> future = pending.remove(id);
        if (future == null) return false;
        future.complete(response);
        return true;
    }

    public void shutdown() {
        scheduler.shutdownNow();
        pending.forEach((k, f) -> f.completeExceptionally(new CancellationException("Manager shutdown")));
        pending.clear();
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>> CompletableFuture<R> sendTransaction(ChannelHandlerContext ctx, T transaction, long timeoutMillis) {
        return this.messenger.sendPacket(ctx, transaction)
                .thenCompose(v -> registerRequest(transaction.getRequestId(), timeoutMillis));
    }
}
