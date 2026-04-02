package dev.sweety.saas.hub;

import dev.sweety.netty.packet.internal.ForwardData;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.server.LoadBalancerServer;
import dev.sweety.saas.hub.backend.ServiceNode;
import dev.sweety.saas.hub.backend.pool.ServicesPool;
import dev.sweety.saas.hub.health.HubHealthServer;
import dev.sweety.saas.hub.security.ConnectionRateLimiter;
import dev.sweety.saas.hub.security.IpWhitelistHandler;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.saas.service.config.ServicesConfig;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import java.util.Optional;

public class ServiceHub extends LoadBalancerServer<ServiceNode> {

    private static final SimpleLogger LOG = new SimpleLogger(ServiceHub.class);

    private final ServicesConfig config;
    private final HubHealthServer healthServer;
    private volatile IpWhitelistHandler ipWhitelist;
    private final ConnectionRateLimiter rateLimiter = new ConnectionRateLimiter();

    public ServiceHub(ServicesConfig config, IPacketRegistry packetRegistry, ServicesPool servicesPool) {
        super(config.hubHost(), config.hubPort(), servicesPool, packetRegistry);
        this.config = config;
        this.healthServer = new HubHealthServer(servicesPool, config.hubHealthPort());
    }

    public void enableIpWhitelist(IpWhitelistHandler.IpProvider ipProvider, int refreshIntervalSeconds) {
        this.ipWhitelist = new IpWhitelistHandler(ipProvider, refreshIntervalSeconds);
        this.healthServer.setIpWhitelist(this.ipWhitelist);
        LOG.info("[Hub] IP whitelist enabled (refresh every " + refreshIntervalSeconds + "s)");
    }

    @Override
    protected void configurePipeline(ChannelPipeline pipeline) {
        pipeline.addLast("rate-limiter", this.rateLimiter);
        if (this.ipWhitelist != null) pipeline.addLast("ip-whitelist", this.ipWhitelist);
    }

    @Override
    public Channel start() {
        this.healthServer.start();
        return super.start();
    }

    public IpWhitelistHandler ipWhitelist() {
        return this.ipWhitelist;
    }

    public ServicesPool services() {
        return ((ServicesPool) backendPool);
    }

    public ServicesConfig config() {
        return config;
    }

    @Override
    public ServiceNode next(InternalPacket packet, ChannelHandlerContext ctx) {
        final Optional<ForwardData> _forward = packet.get();
        if (_forward.isEmpty()) return null;

        final ForwardData forward = _forward.get();

        final ServiceType receiver = ServiceType.of(forward.receiverId());

        return services().get(receiver);
    }

    @Override
    public void stop() {
        if (this.ipWhitelist != null) this.ipWhitelist.shutdown();
        this.rateLimiter.shutdown();
        this.healthServer.stop();
        super.stop();
    }
}