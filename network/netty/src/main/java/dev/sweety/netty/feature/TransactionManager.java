package dev.sweety.netty.feature;

import dev.sweety.thread.ProfileThread;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.PacketTransaction;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.ChannelHandlerContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class TransactionManager {

    private final Map<Long, CompletableFuture<PacketTransaction.Transaction>> pending = Collections.synchronizedMap(new LinkedHashMap<>());

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
        final CompletableFuture<Response> future = (CompletableFuture<Response>) pending.remove(id);
        if (future == null) {
            System.out.println("not registered or expired: " + Long.toHexString(id));
            return false;
        }

        Messenger.safeRun(ctx, c -> future.complete(response));
        return true;
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> CompletableFuture<Response> compose(Transaction packet, Consumer<CompletableFuture<Response>> futureConsumer) {
        //noinspection unchecked
        final CompletableFuture<Response> future = (CompletableFuture<Response>) pending.get(packet.getRequestId());
        if (future == null)
            return CompletableFuture.failedFuture(new IllegalStateException("Request id not registered: " + packet.requestCode()));

        futureConsumer.accept(future);
        return future;
    }

    public boolean failRequest(long requestId, Throwable throwable) {
        final CompletableFuture<PacketTransaction.Transaction> future = pending.remove(requestId);
        if (future == null) {
            return false;
        }
        future.completeExceptionally(throwable != null ? throwable : new CancellationException("Request failed"));
        return true;
    }

    public void shutdown() {
        scheduler.shutdown();
        final Map<Long, CompletableFuture<PacketTransaction.Transaction>> snapshot;
        synchronized (pending) {
            snapshot = new LinkedHashMap<>(pending);
            pending.clear();
        }
        snapshot.forEach((k, f) -> f.completeExceptionally(new CancellationException("Manager shutdown")));
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>>
    CompletableFuture<R> sendTransaction(ChannelHandlerContext ctx, T transaction, long timeoutMillis) {
        final CompletableFuture<R> tracked = registerRequest(transaction, timeoutMillis);
        Messenger.safeRun(ctx, c -> messenger.sendPacket(c, transaction).whenComplete((v, ex) -> {
            if (ex == null) {
                return;
            }
            this.pending.remove(transaction.getRequestId());
            tracked.completeExceptionally(ex);
        }));
        return tracked;
    }

}
