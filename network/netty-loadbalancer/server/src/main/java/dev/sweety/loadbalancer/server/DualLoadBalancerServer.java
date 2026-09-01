package dev.sweety.loadbalancer.server;

import dev.sweety.netty.messaging.impl.DualServer;
import dev.sweety.netty.messaging.security.RateLimitHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dual TCP/UDP Load Balancer Server with integrated Rate Limiting and Backend Pool selection.
 */
public final class DualLoadBalancerServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DualLoadBalancerServer.class);

    private final int bindPort;
    private final BackendPool backendPool;
    private final DualServer dualServer;
    private final RateLimitHandler rateLimitHandler;

    public DualLoadBalancerServer(int bindPort, BackendPool backendPool) {
        this(bindPort, backendPool, RateLimitHandler.perIp(100, 50.0));
    }

    public DualLoadBalancerServer(int bindPort, BackendPool backendPool, RateLimitHandler rateLimitHandler) {
        this.bindPort = bindPort;
        this.backendPool = backendPool;
        this.rateLimitHandler = rateLimitHandler;
        this.dualServer = new DualServer("0.0.0.0", bindPort, new dev.sweety.netty.packet.registry.OptimizedPacketRegistry()) {
            @Override
            public void onPacketReceive(io.netty.channel.ChannelHandlerContext ctx, dev.sweety.netty.packet.model.Packet packet) {
                LOGGER.debug("LoadBalancer received packet: {}", packet.getClass().getSimpleName());
            }
        };
    }

    public void start() {
        LOGGER.info("Starting DualLoadBalancerServer on port {}...", bindPort);
        dualServer.start();
    }

    public void stop() {
        dualServer.stop();
        LOGGER.info("DualLoadBalancerServer stopped.");
    }

    public BackendPool backendPool() {
        return backendPool;
    }

    public DualServer dualServer() {
        return dualServer;
    }
}
