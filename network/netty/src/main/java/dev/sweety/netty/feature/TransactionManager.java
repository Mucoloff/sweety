package dev.sweety.netty.feature;

import dev.sweety.thread.ProfileThread;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.ChannelHandlerContext;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class TransactionManager {

    private static final SimpleLogger logger = SimpleLogger.of(TransactionManager.class);

    /**
     * Hard ceiling on concurrently in-flight RPCs — without it a flood of requests (malicious or a bug
     * looping {@code sendRequest}) grows {@link #pending} unbounded until OOM. Once the cap is hit new
     * registrations fail fast instead of queueing; already in-flight requests are unaffected.
     */
    private static final int MAX_INFLIGHT = 20_000;

    private final Map<Long, CompletableFuture<PacketTransaction.Transaction>> pending = Long2ObjectMaps.synchronize(new Long2ObjectLinkedOpenHashMap<>());

    private final ProfileThread scheduler = new ProfileThread("transaction-manager-thread");

    private final Messenger messenger;

    public TransactionManager(Messenger messenger) {
        this.messenger = messenger;
    }

    public <Response extends PacketTransaction.Transaction, Transaction extends PacketTransaction<?, Response>> CompletableFuture<Response> registerRequest(Transaction packet, long timeoutMillis) {
        return registerRequest(packet.getRequestId(), timeoutMillis);
    }

    public <Response extends PacketTransaction.Transaction> CompletableFuture<Response> registerRequest(long requestId, long timeoutMillis) {
        if (pending.size() >= MAX_INFLIGHT) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("too many in-flight transactions (>= " + MAX_INFLIGHT + ")"));
        }
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
            logger.profile("response").warn("not registered or expired: " + Long.toHexString(id));
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

    /** Fail every pending request (e.g. on disconnect) without shutting the scheduler down — reusable after reconnect. */
    public void failAll(Throwable throwable) {
        final Map<Long, CompletableFuture<PacketTransaction.Transaction>> snapshot;
        synchronized (pending) {
            snapshot = new Long2ObjectLinkedOpenHashMap<>(pending);
            pending.clear();
        }
        final Throwable cause = throwable != null ? throwable : new CancellationException("Request failed");
        snapshot.forEach((k, f) -> { if (!f.isDone()) f.completeExceptionally(cause); });
    }

    public void shutdown() {
        scheduler.shutdown();
        final Map<Long, CompletableFuture<PacketTransaction.Transaction>> snapshot;
        synchronized (pending) {
            snapshot = new Long2ObjectLinkedOpenHashMap<>(pending);
            pending.clear();
        }
        snapshot.forEach((k, f) -> f.completeExceptionally(new CancellationException("Manager shutdown")));
    }

    public <R extends PacketTransaction.Transaction, T extends PacketTransaction<?, R>>
    CompletableFuture<R> sendTransaction(ChannelHandlerContext ctx, T transaction, long timeoutMillis) {
        final CompletableFuture<R> tracked = registerRequest(transaction, timeoutMillis);
        if (tracked.isCompletedExceptionally()) return tracked; // rejected (in-flight cap) — never touch the wire
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
