package dev.sweety.netty.loadbalancer.refact.lb.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.loadbalancer.refact.common.metrics.state.NodeState;
import dev.sweety.netty.loadbalancer.refact.common.packet.InternalPacket;
import dev.sweety.netty.loadbalancer.refact.common.packet.MetricsUpdatePacket;
import dev.sweety.netty.loadbalancer.refact.lb.LBServer;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Node extends Client {

    @Setter
    private LBServer loadBalancer;
    private final SimpleLogger logger = new SimpleLogger(Node.class);

    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);
    private final RequestManager requestManager = new RequestManager();

    public Node(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.logger.push("" + port);
    }

    @Override
    public CompletableFuture<Channel> connect() {
        requestManager.reset();
        usageScore = latencyScore = bandwidthScore = currentBandwidthScore = totalScore = 0f;
        maxObservedAvgLoad = maxObservedCurrentLoad = 1f;
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


    private static final float maxExpectedLatency = 1000f;

    @Getter
    private volatile NodeState state = NodeState.HEALTHY;
    @Getter
    private volatile float usageScore = 0f, latencyScore = 0f, bandwidthScore = 0f, currentBandwidthScore = 0f, totalScore = 0f;
    private volatile float maxObservedAvgLoad = 1f, maxObservedCurrentLoad = 1f;
    synchronized void updateMaxObserved(float avgLoad, float currentLoad) {

        maxObservedAvgLoad = Math.max(maxObservedAvgLoad, avgLoad);
        maxObservedCurrentLoad = Math.max(maxObservedCurrentLoad, currentLoad);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.push("receive", AnsiColor.YELLOW_BRIGHT).info("packet", packet);

        if (packet instanceof MetricsUpdatePacket metrics) {

            float avgLoad = requestManager.getAverageBandwidthLoad();
            float currentLoad = requestManager.getCurrentAverageBandwidthLoad();

            updateMaxObserved(avgLoad, currentLoad);

            bandwidthScore = avgLoad / maxObservedAvgLoad;
            currentBandwidthScore = currentLoad / maxObservedCurrentLoad;

            usageScore = (metrics.cpu() + metrics.ram()) * 0.5f * ((state = metrics.state()) == NodeState.DEGRADED ? 0.7f : 1f);
            latencyScore = MetricsUpdatePacket.clamp(requestManager.getAverageLatency() / maxExpectedLatency);

            totalScore = 0.4f * usageScore + 0.3f * latencyScore + 0.2f * bandwidthScore + 0.1f * currentBandwidthScore;
        } else if (loadBalancer != null && packet instanceof InternalPacket internal) {
            logger.push(internal.requestCode()).info("forwarding to load balancer").pop();
            requestManager.completeRequest(internal.getRequestId());
            loadBalancer.complete(internal);
        }

        logger.pop();
    }

    public void forward(InternalPacket internal) {
        requestManager.addRequest(internal.getRequestId(), internal.buffer().readableBytes());
        logger.push("forward" + internal.requestCode(), AnsiColor.PURPLE_BRIGHT).info("packet:", internal).pop();
        sendPacket(internal);
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
        logger.push("connect").info(ctx.channel().remoteAddress()).pop();
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("disconnect").info(ctx.channel().remoteAddress()).pop();
        promise.setSuccess();
        autoReconnect.onQuit();
    }

}
