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
import java.util.concurrent.atomic.AtomicReference;

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
        this.logger = SimpleLogger.of("Node#" + color);
        this.logger.info("Backend connected!");
    }

    public void learnType(int candidate) {
        if (candidate < 0 || this.typeId == candidate) return;
        // Keep initial compatibility guess, but prefer runtime self-identification.
        this.typeId = candidate;
    }

    public void disconnect() {
        requestMetrics.reset();
        metrics.set(Snapshot.zero());
        packetTimings.clear();
        inFlight.set(0);
        logger.profile("disconnect").info("Backend disconnected!");
    }

    /**
     * Immutable snapshot of all volatile metric fields. Updated atomically via
     * {@link #metrics} to prevent torn reads across related fields.
     */
    private record Snapshot(
            NodeState state,
            double usageScore,
            double latencyScore,
            double bandwidthScore,
            double currentBandwidthScore,
            double packetTimeScore,
            double totalScore,
            double avg_packet_time,
            double maxObservedPacketTime,
            double maxObservedAvgLoad,
            double maxObservedCurrentLoad
    ) {
        static Snapshot zero() {
            return new Snapshot(NodeState.HEALTHY, 0, 0, 0, 0, 0, 0, 0.5, 1, 1, 1);
        }
    }

    private final AtomicReference<Snapshot> metrics = new AtomicReference<>(Snapshot.zero());

    private final Map<Integer, Double> packetTimings = new ConcurrentHashMap<>();

    void updateMaxObserved(double avgLoad, double currentLoad, double currentTime) {
        metrics.updateAndGet(s -> new Snapshot(
                s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(),
                Math.max(s.maxObservedPacketTime(), currentTime),
                Math.max(s.maxObservedAvgLoad(), avgLoad),
                Math.max(s.maxObservedCurrentLoad(), currentLoad)
        ));
    }

    public final double avgPacketTime(int id) {
        return packetTimings.getOrDefault(id, metrics.get().avg_packet_time());
    }

    public boolean handled(Packet packet) {
        return packet instanceof MetricsUpdatePacket || packet instanceof InternalPacket;
    }

    @Override
    public void onPacketReceive(final ChannelHandlerContext ctx, final Packet packet) {
        this.ctx = ctx;
        if (packet instanceof MetricsUpdatePacket metricsPacket) {
            //update packet timings
            packetTimings.putAll(metricsPacket.packetTimings());

            // update max observed scores
            double avgLoad = requestMetrics.getAverageBandwidthLoad();
            double currentLoad = requestMetrics.getCurrentAverageBandwidthLoad();
            double sum_time = 0;
            int count_time = 0;
            for (Double timing : packetTimings.values()) {
                if (timing == null) continue;
                sum_time += timing;
                count_time++;
            }
            final double current_time = count_time > 0 ? sum_time / count_time : 0.5;
            updateMaxObserved(avgLoad, currentLoad, current_time);

            // Compute all new metric values and publish atomically as a single Snapshot.
            metrics.updateAndGet(s -> {
                // Update node state first (so penalty applies consistently)
                final NodeState newState = metricsPacket.state();
                final double statePenalty = (newState == NodeState.DEGRADED ? 0.7f : 1);

                // Resource usage score: weighted blend of process usage + pressure indicators.
                // (CPU/RAM stay the main driver, the others help detect contention / nearing limits.)
                double newUsageScore = statePenalty * (
                        0.32 * metricsPacket.cpu()
                                + 0.28 * metricsPacket.ram()
                                + 0.15 * metricsPacket.openFiles()
                                + 0.15 * metricsPacket.threadPressure()
                                + 0.10 * metricsPacket.systemLoad()
                );

                double newLatencyScore = MathUtils.clamp(requestMetrics.getAverageLatency() / BackendSettings.MAX_EXPECTED_LATENCY());

                // Read the latest max-observed values (already updated above via updateMaxObserved).
                Snapshot cur = metrics.get();
                double newBandwidthScore = avgLoad / cur.maxObservedAvgLoad();
                double newCurrentBandwidthScore = currentLoad / cur.maxObservedCurrentLoad();
                double newPacketTimeScore = current_time / cur.maxObservedPacketTime();

                // Total score: keep previous weights but shift a bit from bandwidth -> usage to reflect new richer usage signal.
                double newTotalScore = 0.40 * newUsageScore
                        + 0.25 * newLatencyScore
                        + 0.15 * newBandwidthScore
                        + 0.10 * newCurrentBandwidthScore
                        + 0.10 * newPacketTimeScore;

                return new Snapshot(
                        newState, newUsageScore, newLatencyScore, newBandwidthScore,
                        newCurrentBandwidthScore, newPacketTimeScore, newTotalScore,
                        current_time,
                        cur.maxObservedPacketTime(), cur.maxObservedAvgLoad(), cur.maxObservedCurrentLoad()
                );
            });
        } else if (loadBalancer != null && packet instanceof InternalPacket internal) {
            internal.get().ifPresent(forward -> {
                if (forward.senderId() >= 0) {
                    learnType(forward.senderId());
                }
            });
            logger.profile("receive").info(internal.requestCode(), internal.hasRequest() ? "request" : internal.hasResponse() ? "response" : "none");
            if (internal.hasResponse()) complete(internal);
        } else logger.profile("receive").warn("Unknown packet type: " + packet);
    }

    public void complete(InternalPacket internal) {
        requestMetrics.completeRequest(internal.getRequestId());
    }

    public void forward(InternalPacket internal) {
        requestMetrics.addRequest(internal.getRequestId(), 0);
        logger.profile("forward").info(internal.requestCode());
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
        return metrics.get().state();
    }

    public BackendNode setState(NodeState state) {
        metrics.updateAndGet(s -> new Snapshot(state, s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double usageScore() {
        return metrics.get().usageScore();
    }

    public BackendNode setUsageScore(double usageScore) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), usageScore, s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double latencyScore() {
        return metrics.get().latencyScore();
    }

    public BackendNode setLatencyScore(double latencyScore) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), latencyScore, s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double bandwidthScore() {
        return metrics.get().bandwidthScore();
    }

    public BackendNode setBandwidthScore(double bandwidthScore) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), bandwidthScore,
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double currentBandwidthScore() {
        return metrics.get().currentBandwidthScore();
    }

    public BackendNode setCurrentBandwidthScore(double currentBandwidthScore) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                currentBandwidthScore, s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double packetTimeScore() {
        return metrics.get().packetTimeScore();
    }

    public BackendNode setPacketTimeScore(double packetTimeScore) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), packetTimeScore, s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double totalScore() {
        return metrics.get().totalScore();
    }

    public BackendNode setTotalScore(double totalScore) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), totalScore,
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double avg_packet_time() {
        return metrics.get().avg_packet_time();
    }

    public BackendNode setAvg_packet_time(double avg_packet_time) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                avg_packet_time, s.maxObservedPacketTime(), s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public double maxObservedPacketTime() {
        return metrics.get().maxObservedPacketTime();
    }

    public BackendNode setMaxObservedPacketTime(double maxObservedPacketTime) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), maxObservedPacketTime, s.maxObservedAvgLoad(), s.maxObservedCurrentLoad()));
        return this;
    }

    public Map<Integer, Double> packetTimings() {
        return packetTimings;
    }

    public double maxObservedAvgLoad() {
        return metrics.get().maxObservedAvgLoad();
    }

    public BackendNode setMaxObservedAvgLoad(double maxObservedAvgLoad) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), maxObservedAvgLoad, s.maxObservedCurrentLoad()));
        return this;
    }

    public double maxObservedCurrentLoad() {
        return metrics.get().maxObservedCurrentLoad();
    }

    public BackendNode setMaxObservedCurrentLoad(double maxObservedCurrentLoad) {
        metrics.updateAndGet(s -> new Snapshot(s.state(), s.usageScore(), s.latencyScore(), s.bandwidthScore(),
                s.currentBandwidthScore(), s.packetTimeScore(), s.totalScore(),
                s.avg_packet_time(), s.maxObservedPacketTime(), s.maxObservedAvgLoad(), maxObservedCurrentLoad));
        return this;
    }

    public AtomicInteger inFlight() {
        return inFlight;
    }

}
