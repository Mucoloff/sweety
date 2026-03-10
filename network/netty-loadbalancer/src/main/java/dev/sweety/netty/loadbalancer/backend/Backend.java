package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.core.thread.ProfileThread;
import dev.sweety.core.thread.ThreadManager;
import dev.sweety.core.thread.ThreadUtil;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.loadbalancer.common.backend.BackendSettings;
import dev.sweety.netty.loadbalancer.common.backend.IBackend;
import dev.sweety.netty.loadbalancer.common.metrics.EMA;
import dev.sweety.netty.loadbalancer.common.packet.internal.ForwardData;
import dev.sweety.netty.loadbalancer.common.packet.internal.InternalPacket;
import dev.sweety.netty.loadbalancer.common.packet.MetricsUpdatePacket;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.record.annotations.DataIgnore;
import dev.sweety.record.annotations.RecordGetter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Backend extends Client implements IBackend {

    private final SimpleLogger backendLogger;
    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    private final ThreadManager threadManager = new ThreadManager("backend-thread-manager");

    private final ScheduledExecutorService metricsScheduler = ThreadUtil.singleThreadScheduler("metrics-sampler");
    private final MetricSampler sampler = new MetricSampler();

    @DataIgnore
    private final AutoReconnect reconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);

    @RecordGetter
    protected final Transactions transactions = new Transactions(this);

    public Backend(final String hubHost, final int hubPort, final IPacketRegistry packetRegistry) {
        this(hubHost, hubPort, packetRegistry, -1);
    }


    public Backend(final String hubHost, final int hubPort, final IPacketRegistry packetRegistry, final int localPort) {
        super(hubHost, hubPort, packetRegistry, localPort);

        this.backendLogger = new SimpleLogger("Backend-" + localPort);
        this.backendLogger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76))).info("Waiting for loadbalancer...");
        this.constructor = (i, ts, data) -> packetRegistry.construct(i, ts, data, this.backendLogger);

        this.sampler.startSampling();
        this.metricsScheduler.scheduleAtFixedRate(this::sendMetrics, BackendSettings.METRICS_DELAY_MS, BackendSettings.METRICS_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void sendMetrics() {
        if (!channel.isActive() || !channel.isOpen() || !channel.isRegistered()) return;
        final MetricsUpdatePacket metricsPacket = new MetricsUpdatePacket(sampler.get(), packetTimings);
        sendPacket(metricsPacket);
    }

    private final Map<Integer, EMA> packetTimings = new ConcurrentHashMap<>();

    private Stream<Packet> calcCpuTime(final int sender, final int receiver, final Packet packet) {
        final long start = System.nanoTime();
        final ArrayList<Packet> results = new ArrayList<>(1);
        handleInternal(sender, receiver, packet, results);
        final float duration = System.nanoTime() - start;
        float ignored = packetTimings.computeIfAbsent(packet.id(), id -> new EMA(BackendSettings.EMA_ALPHA)).update(duration / 1_000_000f);
        //backendLogger.info("cputime for " + packet.id() + ": " + ignored);
        return results.stream();
    }

    private volatile boolean useThreadManager = false;

    public void useThreadManager() {
        this.useThreadManager = true;
    }

    public static long requestTimeout() {
        return BackendSettings.REQUEST_TIMEOUT_SECONDS * 1000L;
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
            Messenger.safeRun(ctx, k -> transactions.sendResponse(internal.getRequestId(), typeId(), request.senderId(), packets));
        };

        if (useThreadManager) {
            final ProfileThread profileThread = this.threadManager.getAvailableProfileThread();
            profileThread.execute(r);
            profileThread.decrement();
        } else r.run();
    }

    Packet @NotNull [] handleForward(final ForwardData forward) {
        final int sender = forward.senderId();
        final int receiver = forward.receiverId();
        return Arrays.stream(forward.decode(this.constructor))
                .flatMap(packet -> calcCpuTime(sender, receiver, packet))
                .filter(Objects::nonNull)
                .filter(p -> !(p instanceof InternalPacket))
                .map(Packet::rewind)
                .toArray(Packet[]::new);
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