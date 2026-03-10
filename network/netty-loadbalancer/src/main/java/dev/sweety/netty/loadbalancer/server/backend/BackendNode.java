package dev.sweety.netty.loadbalancer.server.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.MathUtils;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.loadbalancer.common.backend.BackendSettings;
import dev.sweety.netty.loadbalancer.common.backend.IBackend;
import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;
import dev.sweety.netty.loadbalancer.common.packet.internal.InternalPacket;
import dev.sweety.netty.loadbalancer.common.packet.MetricsUpdatePacket;
import dev.sweety.netty.loadbalancer.server.LoadBalancerServer;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.record.annotations.DataIgnore;
import dev.sweety.record.annotations.RecordGetter;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RecordGetter
public class BackendNode implements IBackend {

    protected final LoadBalancerServer<? extends BackendNode> loadBalancer;
    private final SimpleLogger logger;

    private final int typeId, port;

    protected ChannelHandlerContext ctx;

    @DataIgnore
    private final RequestMatrics requestMatrics = new RequestMatrics();

    public BackendNode(final LoadBalancerServer<? extends BackendNode> loadBalancer, int port, int type) {
        this.loadBalancer = loadBalancer;
        this.typeId = type;
        this.port = port;
        final String color = AnsiColor.fromColor(RandomUtils.RANDOM.nextInt() * type * port) + port + AnsiColor.RESET.getColor();
        this.logger = new SimpleLogger("Node#" + color).info("Backend connected!");
    }

    public void disconnect() {
        requestMatrics.reset();
        usageScore = latencyScore = bandwidthScore = currentBandwidthScore = packetTimeScore = totalScore = 0f;
        maxObservedAvgLoad = maxObservedCurrentLoad = maxObservedPacketTime = 1f;
        packetTimings.clear();
        inFlight = 0;
        logger.push("disconnect").info("Backend disconnected!").pop();
    }

    private volatile NodeState state = NodeState.HEALTHY;
    private volatile float usageScore = 0f, latencyScore = 0f, bandwidthScore = 0f, currentBandwidthScore = 0f, packetTimeScore = 0f, totalScore = 0f;

    private volatile float maxObservedPacketTime = 1f;
    private final Map<Integer, Float> packetTimings = new ConcurrentHashMap<>();

    @DataIgnore
    private volatile float maxObservedAvgLoad = 1f, maxObservedCurrentLoad = 1f;

    synchronized void updateMaxObserved(float avgLoad, float currentLoad, float currentTime) {
        maxObservedAvgLoad = Math.max(maxObservedAvgLoad, avgLoad);
        maxObservedCurrentLoad = Math.max(maxObservedCurrentLoad, currentLoad);
        maxObservedPacketTime = Math.max(maxObservedPacketTime, currentTime);
    }

    public final float avgPacketTime(int id) {
        return packetTimings.getOrDefault(id, maxObservedPacketTime);
    }

    public boolean handled(Packet packet) {
        if (packet instanceof MetricsUpdatePacket) return true;
        if (packet instanceof InternalPacket) return true;
        return false;
    }


    @Override
    public void onPacketReceive(final ChannelHandlerContext ctx, final Packet packet) {
        this.ctx = ctx;
        if (packet instanceof MetricsUpdatePacket metrics) {
            //update packet timings
            packetTimings.putAll(metrics.packetTimings());

            // update max observed scores
            float avgLoad = requestMatrics.getAverageBandwidthLoad();
            float currentLoad = requestMatrics.getCurrentAverageBandwidthLoad();
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

            latencyScore = MathUtils.clamp(requestMatrics.getAverageLatency() / BackendSettings.MAX_EXPECTED_LATENCY);
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
            logger.push("receive", AnsiColor.CYAN_BRIGHT).info(internal.requestCode(), internal.hasRequest() ? "request" : internal.hasResponse() ? "response" : "none").pop();
            if (internal.hasResponse()) complete(internal);
        } else logger.push("receive").warn("Unknown packet type: " + packet).pop();
    }

    public void complete(InternalPacket internal) {
        requestMatrics.completeRequest(internal.getRequestId());
    }

    public void forward(InternalPacket internal) {
        requestMatrics.addRequest(internal.getRequestId(), internal.buffer().readableBytes());
        logger.push("forward", AnsiColor.PURPLE_BRIGHT).info(internal.requestCode()).pop();
        incrementInFlight();
    }

    public <T> CompletableFuture<T> sendToSelf(Packet packet) {
        final ChannelHandlerContext ctx = context();
        return Messenger.safeExecute(ctx, c -> loadBalancer().sendPacket(c, packet));
    }

    public <T> CompletableFuture<T> sendToSelf(Packet... packets) {
        final ChannelHandlerContext ctx = context();
        return Messenger.safeExecute(ctx, c -> loadBalancer().sendPacket(c, packets));
    }

    public ChannelHandlerContext context() {
        if (ctx == null) ctx = loadBalancer().backendPool().context(this);
        return ctx;
    }

    public <T extends BackendNode> T ctx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        //noinspection unchecked
        return ((T) this);
    }

    @DataIgnore
    private volatile int inFlight = 0;

    public synchronized boolean canAcceptPacket() {
        return this.inFlight < BackendSettings.MAX_IN_FLIGHT;
    }

    public synchronized void incrementInFlight() {
        this.inFlight++;
    }

    public synchronized void decrementInFlight() {
        this.inFlight--;
        if (inFlight < BackendSettings.IN_FLIGHT_ACCEPTABLE) {
            loadBalancer.drainPending();
        }
        if (this.inFlight < 0) this.inFlight = 0;
    }

    public void timeout(long requestId) {
        requestMatrics.timeoutRequest(requestId);
    }

    @Override
    public String host() {
        return "none";
    }

    public String typeName() {
        return typeId + ":" + port;
    }
}
