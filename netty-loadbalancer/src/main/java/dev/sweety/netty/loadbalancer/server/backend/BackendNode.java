package dev.sweety.netty.loadbalancer.server.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.MathUtils;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;
import dev.sweety.netty.loadbalancer.common.packet.InternalPacket;
import dev.sweety.netty.loadbalancer.common.packet.MetricsUpdatePacket;
import dev.sweety.netty.loadbalancer.server.LoadBalancerServer;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BackendNode extends Client {

    @Setter
    private LoadBalancerServer loadBalancer;
    private final SimpleLogger logger;

    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);
    private final RequestManager requestManager = new RequestManager();

    public BackendNode(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.logger = new SimpleLogger("Node#" + AnsiColor.fromColor(RandomUtils.RANDOM.nextInt() * port) + port + AnsiColor.RESET.getColor()).info("Waiting for backend...");
    }

    @Override
    public CompletableFuture<Channel> connect() {
        requestManager.reset();
        usageScore = latencyScore = bandwidthScore = currentBandwidthScore = packetTimeScore = totalScore = 0f;
        maxObservedAvgLoad = maxObservedCurrentLoad = maxObservedPacketTime = 1f;
        return super.connect().exceptionally(t -> {
            this.autoReconnect.onException(t);
            return null;
        });
    }

    @Override
    public void stop() {
        this.autoReconnect.shutdown();
        super.stop();
    }

    //todo make a settings class for these
    private static final int maxInFlight = 50, inFlightAcceptable = 35;
    private static final float maxExpectedLatency = 1000f;

    @Getter
    private volatile NodeState state = NodeState.HEALTHY;
    @Getter
    private volatile float usageScore = 0f, latencyScore = 0f, bandwidthScore = 0f, currentBandwidthScore = 0f, packetTimeScore = 0f, totalScore = 0f;
    private volatile float maxObservedAvgLoad = 1f, maxObservedCurrentLoad = 1f;

    @Getter
    private volatile float maxObservedPacketTime = 1f;

    synchronized void updateMaxObserved(float avgLoad, float currentLoad, float currentTime) {
        maxObservedAvgLoad = Math.max(maxObservedAvgLoad, avgLoad);
        maxObservedCurrentLoad = Math.max(maxObservedCurrentLoad, currentLoad);
        maxObservedPacketTime = Math.max(maxObservedPacketTime, currentTime);
    }

    @Getter
    private final Map<Integer, Float> packetTimings = new ConcurrentHashMap<>();

    public final float getAvgPacketTime(int id) {
        return packetTimings.getOrDefault(id, maxObservedPacketTime);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof MetricsUpdatePacket metrics) {
            //update packet timings
            packetTimings.putAll(metrics.packetTimings());

            // update max observed scores
            float avgLoad = requestManager.getAverageBandwidthLoad();
            float currentLoad = requestManager.getCurrentAverageBandwidthLoad();
            float currentTime = (float) packetTimings.values().stream().mapToDouble(Float::doubleValue).average().orElse(0.5f);
            updateMaxObserved(avgLoad, currentLoad, currentTime);

            // Update node state first (so penalty applies consistently)
            final float statePenalty = ((state = metrics.state()) == NodeState.DEGRADED ? 0.7f : 1f);

            // Resource usage score: weighted blend of process usage + pressure indicators.
            // (CPU/RAM stay the main driver, the others help detect contention / nearing limits.)
            usageScore = statePenalty * (
                    0.32f * metrics.cpu()
                            + 0.28f * metrics.ram()
                            + 0.15f * metrics.openFiles()
                            + 0.15f * metrics.threadPressure()
                            + 0.10f * metrics.systemLoad()
            );

            latencyScore = MathUtils.clamp(requestManager.getAverageLatency() / maxExpectedLatency);
            bandwidthScore = avgLoad / maxObservedAvgLoad;
            currentBandwidthScore = currentLoad / maxObservedCurrentLoad;
            packetTimeScore = currentTime / maxObservedPacketTime;

            // Total score: keep previous weights but shift a bit from bandwidth -> usage to reflect new richer usage signal.
            totalScore = 0.40f * usageScore
                    + 0.25f * latencyScore
                    + 0.15f * bandwidthScore
                    + 0.10f * currentBandwidthScore
                    + 0.10f * packetTimeScore;

        } else if (loadBalancer != null && packet instanceof InternalPacket internal) {
            requestManager.completeRequest(internal.getRequestId());
            loadBalancer.complete(internal, ctx);
        }
    }

    public void forward(ChannelHandlerContext ctx, InternalPacket internal) {
        requestManager.addRequest(internal.getRequestId(), internal.buffer().readableBytes());
        logger.push("forward", AnsiColor.PURPLE_BRIGHT).info(internal.requestCode()).pop();
        incrementInFlight();
        Messenger.safeExecute(ctx, c -> sendPacket(internal).whenComplete((a, b) -> decrementInFlight()));
    }


    private volatile int inFlight = 0;

    public synchronized boolean canAcceptPacket() {
        return this.inFlight < maxInFlight;
    }

    public synchronized void incrementInFlight() {
        this.inFlight++;
    }

    public synchronized void decrementInFlight() {
        this.inFlight--;
        if (inFlight < inFlightAcceptable) loadBalancer.drainPending();
        if (this.inFlight < 0) this.inFlight = 0;
    }

    public void timeout(long requestId) {
        requestManager.timeoutRequest(requestId);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        autoReconnect.onException(throwable);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect", AnsiColor.GREEN_BRIGHT).info("backend " + port + " connected").pop();
        promise.setSuccess();
        loadBalancer.drainPending();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("disconnect", AnsiColor.RED_BRIGHT).info("backend " + port + " disconnected").pop();
        promise.setSuccess();
        autoReconnect.onQuit();
    }

}
