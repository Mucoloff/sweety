package dev.sweety.netty.backend;

import dev.sweety.color.AnsiColor;
import dev.sweety.netty.common.backend.BackendSettings;
import dev.sweety.netty.common.backend.IBackend;
import dev.sweety.netty.metrics.EMA;
import dev.sweety.netty.packet.MetricsUpdatePacket;
import dev.sweety.netty.packet.Packer;
import dev.sweety.netty.packet.internal.ForwardData;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.math.function.TriFunction;
import dev.sweety.thread.ThreadManager;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class Backend extends Client implements IBackend {

    private static final ThreadLocal<ArrayList<Packet>> RESULT_POOL = ThreadLocal.withInitial(() -> new ArrayList<>(8));
    private static final ThreadLocal<ArrayList<Packet>> FORWARD_POOL = ThreadLocal.withInitial(() -> new ArrayList<>(32));

    private final SimpleLogger backendLogger;
    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    private final ThreadManager threadManager = new ThreadManager("backend-thread-manager");

    private final ScheduledExecutorService metricsScheduler = ThreadUtil.singleThreadScheduler("metrics-sampler");
    private final MetricSampler sampler = new MetricSampler();

    private final AutoReconnect reconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);

    protected final Transactions transactions = new Transactions(this);

    public Transactions transactions() {
        return transactions;
    }

    public Backend(final String hubHost, final int hubPort, final IPacketRegistry packetRegistry) {
        this(hubHost, hubPort, packetRegistry, -1);
    }

    public Backend(final String hubHost, final int hubPort, final IPacketRegistry packetRegistry, final int localPort) {
        super(hubHost, hubPort, packetRegistry, localPort);
        BackendSettings.REQUEST_TIMEOUT_SECONDS(
                Long.parseLong(System.getenv().getOrDefault("BACKEND_REQUEST_TIMEOUT_SECONDS", "45"))
        );

        this.backendLogger = new SimpleLogger("Backend-" + localPort);
        this.backendLogger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76))).info("Waiting for loadbalancer...");
        this.constructor = (i, ts, data) -> packetRegistry.construct(i, ts, data, this.backendLogger);

        this.sampler.startSampling();
        this.metricsScheduler.scheduleAtFixedRate(this::sendMetrics, BackendSettings.METRICS_DELAY_MS(), BackendSettings.METRICS_DELAY_MS(), TimeUnit.MILLISECONDS);
    }

    private void sendMetrics() {
        if (!channel.isActive() || !channel.isOpen() || !channel.isRegistered()) return;
        final MetricsUpdatePacket metricsPacket = new MetricsUpdatePacket(sampler.get(), packetTimings);
        sendPacket(metricsPacket);
    }

    private final Map<Integer, EMA> packetTimings = new ConcurrentHashMap<>();

    private ArrayList<Packet> calcCpuTime(final int sender, final int receiver, final Packet packet) {
        final long start = System.nanoTime();
        final ArrayList<Packet> results = RESULT_POOL.get();
        results.clear();
        handleInternal(sender, receiver, packet, results);
        final float duration = System.nanoTime() - start;
        packetTimings.computeIfAbsent(packet.id(), id -> new EMA(BackendSettings.EMA_ALPHA())).update(duration / 1_000_000f);
        return results;
    }

    private volatile boolean useThreadManager = false;

    public void useThreadManager() {
        this.useThreadManager = true;
    }

    public static long requestTimeout() {
        return BackendSettings.REQUEST_TIMEOUT_SECONDS() * 1000L;
    }

    @Override
    public void onPacketReceive(final ChannelHandlerContext ctx, final Packet packet) {
        if (!(packet instanceof InternalPacket internal)) return;

        if (internal.hasResponse()) {
            if (!transactions.completeResponse(internal, ctx)) {
                backendLogger.push("expired", AnsiColor.RED_BRIGHT).warn(internal.requestCode()).pop();
            }
            return;
        }

        if (!internal.hasRequest()) return;
        final Runnable r = () -> {
            final ForwardData request = internal.getRequest();
            final Packet[] packets = handleForward(request);
            if (packets.length == 0) {
                // Fire-and-forget: no one is waiting for a response, skip ACK
                if (internal.isFireAndForget()) return;
                // Keep async transaction flows open (they will answer later with same requestId),
                // but ACK non-transaction forwards to avoid Hub timeout storms.
                if (containsAsyncTransactionRequest(request)) return;
                Messenger.safeRun(ctx, k -> transactions.sendResponse(
                        internal.getRequestId(),
                        typeId(),
                        request.senderId()
                ));
                return;
            }
            Messenger.safeRun(ctx, k -> transactions.sendResponse(internal.getRequestId(), typeId(), request.senderId(), packets));
        };

        if (useThreadManager) {
            this.threadManager.fireAndForget(t -> t.execute(r));
        } else r.run();
    }

    Packet @NotNull [] handleForward(final ForwardData forward) {
        final int sender = forward.senderId();
        final int receiver = forward.receiverId();
        final Packet[] decoded = forward.decode(this.constructor);

        if (decoded.length == 0) return Packer.EMPTY();

        final ArrayList<Packet> forwarded = FORWARD_POOL.get();
        forwarded.clear();
        for (final Packet source : decoded) {
            if (source == null) continue;

            final ArrayList<Packet> results = calcCpuTime(sender, receiver, source);
            for (final Packet out : results) {
                if (out == null || out instanceof InternalPacket) continue;
                forwarded.add(out.rewind());
            }
        }
        if (forwarded.isEmpty()) return Packer.EMPTY();
        return forwarded.toArray(Packet[]::new);
    }

    private boolean containsAsyncTransactionRequest(final ForwardData request) {
        final Packet[] decoded = request.decode(this.constructor);
        for (final Packet candidate : decoded) {
            if (!(candidate instanceof PacketTransaction<?, ?> tx)) continue;
            if (tx.hasRequest() && !tx.hasResponse()) return true;
        }
        return false;
    }

    @Override
    public CompletableFuture<Channel> connect() {
        this.sampler.reset();
        return super.connect().exceptionally((t) -> {
            this.reconnect.onException(t);
            return null;
        }).whenComplete((c, t) -> {
            if (c != null) this.reconnect.complete();
        });
    }

    @Override
    public void exception(final ChannelHandlerContext ctx, final Throwable throwable) {
        if (!reconnect.onException(throwable)) backendLogger.push("exception").error(throwable).pop();
    }

    @Override
    public void join(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        backendLogger.push("connect", AnsiColor.GREEN_BRIGHT).info("backend " + localPort + " connected").pop();
        promise.setSuccess();
    }

    @Override
    public final void quit(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        sampler.reset();
        backendLogger.push("disconnect", AnsiColor.RED_BRIGHT).info("backend " + localPort + " disconnected").pop();
        promise.setSuccess();
        reconnect.onQuit();
        this.leave(ctx);
    }

    @Override
    public void stop() {
        super.stop();
        metricsScheduler.shutdown();
        threadManager.shutdown();
        this.reconnect.shutdown();
        this.transactions.shutdown();
    }

    public SimpleLogger backendLogger() {
        return backendLogger;
    }

    public void handleInternal(final int sender, final int receiver, final Packet packet, final ArrayList<Packet> results) {
        handleInternal(packet, results);
    }

    public abstract void handleInternal(final Packet packet, final ArrayList<Packet> results);

    public abstract void leave(final ChannelHandlerContext ctx);

    public String typeName() {
        return "%d".formatted(typeId());
    }

    public String convertType(int type) {
        return "%d".formatted(type);
    }

}