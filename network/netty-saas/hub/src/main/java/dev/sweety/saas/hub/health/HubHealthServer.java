package dev.sweety.saas.hub.health;

import dev.sweety.saas.hub.backend.ServiceNode;
import dev.sweety.saas.hub.backend.pool.ServicesPool;
import dev.sweety.saas.hub.security.IpWhitelistHandler;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.Map;
import java.util.Set;

/**
 * Minimal HTTP server running on HUB_HEALTH_PORT (default: hubPort + 1, e.g. 4001).
 * Supports JSON health endpoints and Prometheus exposition format (/metrics).
 */
public final class HubHealthServer {

    private static final SimpleLogger LOG = SimpleLogger.of(HubHealthServer.class);

    private final ServicesPool pool;
    private final int port;
    private final long startedAt = System.currentTimeMillis();

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private volatile IpWhitelistHandler ipWhitelist;

    public HubHealthServer(final ServicesPool pool, final int hubHealthPort) {
        this.pool = pool;
        this.port = hubHealthPort;
    }

    public void setIpWhitelist(IpWhitelistHandler ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public void start() {
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup(2);
        try {
            new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(4096))
                                    .addLast(new HealthHandler());
                        }
                    })
                    .bind(port).sync();
            LOG.info("[HEALTH] HTTP server listening on :" + port
                    + " — endpoints: /api/health  /api/health/ready  /api/health/{TYPE}  /metrics");
        } catch (Exception e) {
            LOG.error("[HEALTH] Failed to start HTTP server on port " + port, e);
        }
    }

    public void stop() {
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
        if (workerGroup != null)
            workerGroup.shutdownGracefully();
    }

    // ── HTTP handler ──────────────────────────────────────────────────────────

    private final class HealthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            final String path = req.uri().split("\\?")[0].toLowerCase();

            if (path.equals("/api/health")) {
                respondJson(ctx, buildFullHealth(), HttpResponseStatus.OK);
                return;
            }

            if (path.equals("/api/health/ready")) {
                final Set<ServiceType> connected = pool.connectedTypes();
                final boolean allUp = allExpectedUp(connected);
                respondJson(ctx,
                        "{\"ready\":" + allUp + ",\"connected\":" + connected.size() + "}",
                        allUp ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
                return;
            }

            if (path.equals("/metrics")) {
                respondText(ctx, buildPrometheusMetrics(), "text/plain; version=0.0.4; charset=UTF-8");
                return;
            }

            // /api/health/{TYPE}
            if (path.startsWith("/api/health/")) {
                final String typeName = path.substring("/api/health/".length()).toUpperCase();
                try {
                    final ServiceType type = ServiceType.of(typeName);
                    final boolean up = pool.isConnected(type);
                    respondJson(ctx,
                            "{\"service\":\"" + type.name() + "\",\"status\":\"" + (up ? "UP" : "DOWN") + "\"}",
                            up ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
                } catch (IllegalArgumentException e) {
                    respondJson(ctx, "{\"error\":\"Unknown service type: " + typeName + "\"}",
                            HttpResponseStatus.BAD_REQUEST);
                }
                return;
            }

            respondJson(ctx, "{\"error\":\"Not found\"}", HttpResponseStatus.NOT_FOUND);
        }

        private void respondJson(ChannelHandlerContext ctx, String body, HttpResponseStatus status) {
            respondText(ctx, body, "application/json; charset=UTF-8", status);
        }

        private void respondText(ChannelHandlerContext ctx, String body, String contentType) {
            respondText(ctx, body, contentType, HttpResponseStatus.OK);
        }

        private void respondText(ChannelHandlerContext ctx, String body, String contentType, HttpResponseStatus status) {
            final byte[] bytes = body.getBytes(CharsetUtil.UTF_8);
            final FullHttpResponse resp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
            resp.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, contentType)
                    .set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(bytes.length))
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    // ── Prometheus & JSON builders ──────────────────────────────────────────

    private String buildPrometheusMetrics() {
        final StringBuilder sb = new StringBuilder(1024);
        final long uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000L;

        sb.append("# HELP sweety_hub_uptime_seconds Hub server uptime in seconds\n");
        sb.append("# TYPE sweety_hub_uptime_seconds counter\n");
        sb.append("sweety_hub_uptime_seconds ").append(uptimeSeconds).append("\n\n");

        sb.append("# HELP sweety_service_nodes_connected Total connected backend nodes per service type\n");
        sb.append("# TYPE sweety_service_nodes_connected gauge\n");

        final Map<ServiceType, ServicesPool.ServiceCluster> clusters = pool.viewClusters();
        for (final ServiceType type : ServiceType.values()) {
            final ServicesPool.ServiceCluster cluster = clusters.get(type);
            final int count = cluster != null ? cluster.nodes.size() : 0;
            sb.append("sweety_service_nodes_connected{service=\"").append(type.name()).append("\"} ").append(count).append("\n");
        }

        sb.append("\n# HELP sweety_service_node_in_flight In-flight active requests per service node\n");
        sb.append("# TYPE sweety_service_node_in_flight gauge\n");
        for (Map.Entry<ServiceType, ServicesPool.ServiceCluster> entry : clusters.entrySet()) {
            final String serviceName = entry.getKey().name();
            for (ServiceNode node : entry.getValue().nodes) {
                sb.append("sweety_service_node_in_flight{service=\"").append(serviceName)
                        .append("\",host=\"").append(node.host() != null ? node.host() : "unknown")
                        .append("\",port=\"").append(node.port()).append("\"} ")
                        .append(node.inFlight()).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildFullHealth() {
        final Set<ServiceType> connected = pool.connectedTypes();
        final Map<ServiceType, ServicesPool.ServiceCluster> clusters = pool.viewClusters();
        final long uptime = System.currentTimeMillis() - startedAt;
        final boolean allUp = allExpectedUp(connected);

        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":\"").append(allUp ? "UP" : "DEGRADED").append("\",");
        sb.append("\"uptime_ms\":").append(uptime).append(",");
        sb.append("\"services\":{");

        boolean first = true;
        for (final ServiceType type : ServiceType.values()) {
            if (!first)
                sb.append(",");
            first = false;
            final boolean up = connected.contains(type);
            sb.append("\"").append(type.name()).append("\":{");
            sb.append("\"status\":\"").append(up ? "UP" : "DOWN").append("\"");
            
            if (up && clusters.containsKey(type)) {
                ServicesPool.ServiceCluster cluster = clusters.get(type);
                sb.append(",\"nodes\":[");
                boolean firstNode = true;
                for (Map.Entry<ServiceNode, Long> entry : cluster.connectedAt.entrySet()) {
                    if (!firstNode) sb.append(",");
                    firstNode = false;
                    sb.append("{\"host\":\"").append(entry.getKey().host()).append("\",\"connected_at\":").append(entry.getValue()).append("}");
                }
                sb.append("]");
            }
            sb.append("}");
        }

        sb.append("}");

        if (ipWhitelist != null) {
            sb.append(",\"ipWhitelist\":{");
            sb.append("\"active\":").append(ipWhitelist.isActive()).append(",");
            sb.append("\"size\":").append(ipWhitelist.whitelistSize()).append(",");
            sb.append("\"lastRefresh\":").append(ipWhitelist.lastRefreshMs());
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    private static boolean allExpectedUp(final Set<ServiceType> connected) {
        return connected.containsAll(ServiceType.requiredValues());
    }
}
