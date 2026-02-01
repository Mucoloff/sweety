package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.core.thread.ProfileThread;
import dev.sweety.core.thread.ThreadManager;
import dev.sweety.core.thread.ThreadUtil;
import dev.sweety.netty.loadbalancer.common.metrics.EMA;
import dev.sweety.netty.loadbalancer.common.packet.InternalPacket;
import dev.sweety.netty.loadbalancer.common.packet.MetricsUpdatePacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Backend extends Server {

    private final SimpleLogger _logger;
    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    private final ThreadManager threadManager = new ThreadManager("backend-thread-manager");

    private final ScheduledExecutorService metricsScheduler = ThreadUtil.namedScheduler("metrics-sampler");
    private final MetricSampler sampler = new MetricSampler();

    public Backend(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        this._logger = new SimpleLogger("Backend-" + port);
        this._logger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76))).info("Waiting for loadbalancer...");
        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this._logger);

        //todo make a settings class for these
        final int delay = 2500;

        this.sampler.startSampling();
        this.metricsScheduler.scheduleAtFixedRate(this::sendMetrics, delay, delay, TimeUnit.MILLISECONDS);
    }

    private void sendMetrics() {
        if (!channel.isActive() || !channel.isOpen() || !channel.isRegistered() || getClients().isEmpty()) return;

        final MetricsUpdatePacket metricsPacket = new MetricsUpdatePacket(sampler.get(), packetTimings);
        broadcastPacket(metricsPacket);
    }

    private final Map<Integer, EMA> packetTimings = new ConcurrentHashMap<>();

    private Stream<Packet> calcCpuTime(final Packet packet) {
        final long start = System.nanoTime();
        final Packet[] handled = handlePackets(packet);
        final long duration = System.nanoTime() - start;
        packetTimings.computeIfAbsent(packet.id(), id -> new EMA(0.35f)).update(duration / 1_000_000f);
        return Arrays.stream(handled);
    }

    private volatile boolean useThreadManager = false;

    public void useThreadManager() {
        this.useThreadManager = false;
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof InternalPacket internal) || !internal.hasRequest()) return;

        Runnable r = () -> {
            final InternalPacket.Forward request = internal.getRequest();
            final List<Packet> handledPackets = Arrays.stream(request.decode(this.constructor))
                    .flatMap(this::calcCpuTime)
                    .collect(Collectors.toList());

            handledPackets.removeIf(Objects::isNull);
            handledPackets.removeIf(p -> p instanceof InternalPacket);
            final Packet[] packets = handledPackets.toArray(handledPackets.toArray(Packet[]::new));
            final InternalPacket.Forward response = new InternalPacket.Forward(getPacketRegistry()::getPacketId, packets);

            Messenger.safeExecute(ctx, c -> sendPacket(c, new InternalPacket(internal.getRequestId(), response)));
        };

        if (useThreadManager){
            final ProfileThread profileThread = this.threadManager.getAvailableProfileThread();
            profileThread.execute(r);
            profileThread.decrement();
        } else r.run();

    }

    @Override
    public CompletableFuture<Channel> connect() {
        this.sampler.reset();
        return super.connect();
    }

    @Override
    public final void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        sampler.reset();
        threadManager.shutdown();
        this.leave(ctx, promise);
    }

    @Override
    public void stop() {
        super.stop();
        metricsScheduler.shutdown();
        threadManager.shutdown();
    }

    public SimpleLogger _logger() {
        return _logger;
    }

    public abstract Packet[] handlePackets(Packet packet);

    public abstract void leave(ChannelHandlerContext ctx, ChannelPromise promise);

}
