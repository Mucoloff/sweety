package dev.sweety.netty.server.backend;

import dev.sweety.color.AnsiColor;
import dev.sweety.netty.common.backend.BackendSettings;
import dev.sweety.netty.common.backend.IBackend;
import dev.sweety.netty.metrics.state.NodeState;
import dev.sweety.netty.server.LoadBalancerServer;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.math.MathUtils;
import dev.sweety.math.RandomUtils;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.MetricsUpdatePacket;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BackendNode implements IBackend {

    protected final LoadBalancerServer<? extends BackendNode> loadBalancer;
    private final SimpleLogger logger;

    private volatile int typeId;
    private final int port;

    protected ChannelHandlerContext ctx;

    private final RequestMetrics requestMetrics = new RequestMetrics();

    public BackendNode(final LoadBalancerServer<? extends BackendNode> loadBalancer, int port, int type) {
        this.loadBalancer = loadBalancer;
        this.typeId = type;
        this.port = port;
        final String color = AnsiColor.fromColor(RandomUtils.RANDOM.nextInt() * type * port) + port + AnsiColor.RESET.color();
        this.logger = new SimpleLogger("Node#" + color).info("Backend connected!");
    }

    public void learnType(int candidate) {
        if (candidate < 0 || this.typeId == candidate) {
            return;
        }
        // Keep initial compatibility guess, but prefer runtime self-identification.
        this.typeId = candidate;
    }

    public void disconnect() {
        requestMetrics.reset();
        usageScore = latencyScore = bandwidthScore = currentBandwidthScore = packetTimeScore = totalScore = 0f;
        maxObservedAvgLoad = maxObservedCurrentLoad = maxObservedPacketTime = 1f;
        packetTimings.clear();
        inFlight.set(0);
        logger.push("disconnect").info("Backend disconnected!").pop();
    }

    private volatile NodeState state = NodeState.HEALTHY;
    private volatile float usageScore = 0f, latencyScore = 0f, bandwidthScore = 0f, currentBandwidthScore = 0f, packetTimeScore = 0f, totalScore = 0f;
    private volatile float avg_packet_time = 0.5f;

    private volatile float maxObservedPacketTime = 1f;
    private final Map<Integer, Float> packetTimings = new ConcurrentHashMap<>();

    private volatile float maxObservedAvgLoad = 1f, maxObservedCurrentLoad = 1f;

    synchronized void updateMaxObserved(float avgLoad, float currentLoad, float currentTime) {
        maxObservedAvgLoad = Math.max(maxObservedAvgLoad, avgLoad);
        maxObservedCurrentLoad = Math.max(maxObservedCurrentLoad, currentLoad);
        maxObservedPacketTime = Math.max(maxObservedPacketTime, currentTime);
    }

    public final float avgPacketTime(int id) {
        return packetTimings.getOrDefault(id, avg_packet_time);
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
            float avgLoad = requestMetrics.getAverageBandwidthLoad();
            float currentLoad = requestMetrics.getCurrentAverageBandwidthLoad();
            float sum_time = 0f;
            int count_time = 0;
            for (Float timing : packetTimings.values()) {
                if (timing == null) continue;
                sum_time += timing;
                count_time++;
            }
            final float current_time = count_time > 0 ? sum_time / count_time : 0.5f;
            this.avg_packet_time = current_time;
            updateMaxObserved(avgLoad, currentLoad, current_time);

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

            latencyScore = MathUtils.clamp(requestMetrics.getAverageLatency() / BackendSettings.MAX_EXPECTED_LATENCY());
            bandwidthScore = avgLoad / maxObservedAvgLoad;
            currentBandwidthScore = currentLoad / maxObservedCurrentLoad;
            packetTimeScore = current_time / maxObservedPacketTime;

            // Total score: keep previous weights but shift a bit from bandwidth -> usage to reflect new richer usage signal.
            totalScore = 0.40f * usageScore
                    + 0.25f * latencyScore
                    + 0.15f * bandwidthScore
                    + 0.10f * currentBandwidthScore
                    + 0.10f * packetTimeScore;

        } else if (loadBalancer != null && packet instanceof InternalPacket internal) {
            internal.get().ifPresent(forward -> {
                if (forward.senderId() >= 0) {
                    learnType(forward.senderId());
                }
            });
            logger.push("receive", AnsiColor.CYAN_BRIGHT).info(internal.requestCode(), internal.hasRequest() ? "request" : internal.hasResponse() ? "response" : "none").pop();
            if (internal.hasResponse()) complete(internal);
        } else logger.push("receive").warn("Unknown packet type: " + packet).pop();
    }

    public void complete(InternalPacket internal) {
        requestMetrics.completeRequest(internal.getRequestId());
    }

    public void forward(InternalPacket internal) {
        requestMetrics.addRequest(internal.getRequestId(), internal.buffer().readableBytes());
        logger.push("forward", AnsiColor.PURPLE_BRIGHT).info(internal.requestCode()).pop();
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

    private final AtomicInteger inFlight = new AtomicInteger(0);

    public boolean canAcceptPacket() {
        return this.inFlight.get() < BackendSettings.MAX_IN_FLIGHT;
    }

    public boolean tryAcceptPacket() {
        int current;
        do {
            current = this.inFlight.get();
            if (current >= BackendSettings.MAX_IN_FLIGHT) {
                return false;
            }
        } while (!this.inFlight.compareAndSet(current, current + 1));
        return true;
    }

    public void incrementInFlight() {
        this.inFlight.incrementAndGet();
    }

    public void decrementInFlight() {
        int current = this.inFlight.decrementAndGet();
        if (current < BackendSettings.IN_FLIGHT_ACCEPTABLE) {
            loadBalancer.drainPending();
        }
        if (current < 0) this.inFlight.set(0);
    }

    public void timeout(long requestId) {
        requestMetrics.timeoutRequest(requestId);
    }

    @Override
    public String host() {
        return "none";
    }

    public String typeName() {
        return typeId + ":" + port;
    }

    public LoadBalancerServer<? extends BackendNode> loadBalancer() {
        return loadBalancer;
    }

    public SimpleLogger logger() {
        return logger;
    }

    @Override
    public int typeId() {
        return typeId;
    }

    public BackendNode setTypeId(int typeId) {
        this.typeId = typeId;
        return this;
    }

    @Override
    public int port() {
        return port;
    }

    public ChannelHandlerContext ctx() {
        return ctx;
    }

    public BackendNode setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        return this;
    }

    public RequestMetrics requestMatrics() {
        return requestMetrics;
    }

    public NodeState state() {
        return state;
    }

    public BackendNode setState(NodeState state) {
        this.state = state;
        return this;
    }

    public float usageScore() {
        return usageScore;
    }

    public BackendNode setUsageScore(float usageScore) {
        this.usageScore = usageScore;
        return this;
    }

    public float latencyScore() {
        return latencyScore;
    }

    public BackendNode setLatencyScore(float latencyScore) {
        this.latencyScore = latencyScore;
        return this;
    }

    public float bandwidthScore() {
        return bandwidthScore;
    }

    public BackendNode setBandwidthScore(float bandwidthScore) {
        this.bandwidthScore = bandwidthScore;
        return this;
    }

    public float currentBandwidthScore() {
        return currentBandwidthScore;
    }

    public BackendNode setCurrentBandwidthScore(float currentBandwidthScore) {
        this.currentBandwidthScore = currentBandwidthScore;
        return this;
    }

    public float packetTimeScore() {
        return packetTimeScore;
    }

    public BackendNode setPacketTimeScore(float packetTimeScore) {
        this.packetTimeScore = packetTimeScore;
        return this;
    }

    public float totalScore() {
        return totalScore;
    }

    public BackendNode setTotalScore(float totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public float avg_packet_time() {
        return avg_packet_time;
    }

    public BackendNode setAvg_packet_time(float avg_packet_time) {
        this.avg_packet_time = avg_packet_time;
        return this;
    }

    public float maxObservedPacketTime() {
        return maxObservedPacketTime;
    }

    public BackendNode setMaxObservedPacketTime(float maxObservedPacketTime) {
        this.maxObservedPacketTime = maxObservedPacketTime;
        return this;
    }

    public Map<Integer, Float> packetTimings() {
        return packetTimings;
    }

    public float maxObservedAvgLoad() {
        return maxObservedAvgLoad;
    }

    public BackendNode setMaxObservedAvgLoad(float maxObservedAvgLoad) {
        this.maxObservedAvgLoad = maxObservedAvgLoad;
        return this;
    }

    public float maxObservedCurrentLoad() {
        return maxObservedCurrentLoad;
    }

    public BackendNode setMaxObservedCurrentLoad(float maxObservedCurrentLoad) {
        this.maxObservedCurrentLoad = maxObservedCurrentLoad;
        return this;
    }

    public AtomicInteger inFlight() {
        return inFlight;
    }
}
