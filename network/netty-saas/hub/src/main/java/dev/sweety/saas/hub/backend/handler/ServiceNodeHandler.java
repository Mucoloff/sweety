package dev.sweety.saas.hub.backend.handler;

import dev.sweety.netty.metrics.SmoothedLoad;
import dev.sweety.netty.packet.MetricsUpdatePacket;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.saas.hub.backend.ServiceNode;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;

public abstract class ServiceNodeHandler {

    private final ServiceNode node;

    protected ServiceNodeHandler(final ServiceNode node) {
        this.node = node;
    }

    public abstract void handle(final ChannelHandlerContext ctx, final Packet packet);

    public abstract boolean handled(Packet packet);

    public ServiceNode node() {
        return node;
    }

    public void keepAlive(double timing) {

    }

    public void metricsUpdate(SmoothedLoad load) {

    }

    public void metricsUpdate(Map<String, Long> metrics) {

    }

    public void disconnect() {

    }
}
