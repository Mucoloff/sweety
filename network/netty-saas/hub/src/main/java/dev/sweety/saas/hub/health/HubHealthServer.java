package dev.sweety.saas.hub.health;

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
 * Minimal HTTP server running on HUB_HEALTH_PORT (default: hubPort + 1, e.g.
 * 4001).
 * <p>
 * Endpoints:
 * GET /api/health → full topology JSON
 * GET /api/health/ready → 200 if ALL expected services connected, 503 otherwise
 * GET /api/health/{type} → 200/503 for a single ServiceType (e.g.
 * /api/health/DATABASE)
 * <p>
 * Example response for /api/health:
 * {
 * "status": "UP",
 * "uptime_ms": 12345,
 * "services": {
 * "DATABASE": { "status": "UP", "connected_at": 1700000000123 },
 * "IA": { "status": "UP", "connected_at": 1700000000456 },
 * "ANTICHEAT": { "status": "UP", "connected_at": 1700000000789 },
 * "LOBBY": { "status": "UP", "connected_at": 1700000001000 },
 * "WEB": { "status": "DOWN" }
 * }
 * }
 */
public final class HubHealthServer {

    private static final SimpleLogger LOG = new SimpleLogger(HubHealthServer.class);

    private final ServicesPool pool;
    private final int port;
    private final long startedAt = System.currentTimeMillis();

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private volatile IpWhitelistHandler ipWhitelist;

    public HubHealthServer(final ServicesPool pool, final int hubPort) {
        this.pool = pool;
        // health port = hubPort + 1 by default, overridable via env
        this.port = Integer.parseInt(
                System.getenv().getOrDefault("HUB_HEALTH_PORT", String.valueOf(hubPort + 1)));
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
                    + " — endpoints: /api/health  /api/health/ready  /api/health/{TYPE}");
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
        protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest req) {
            final String path = req.uri().split("\\?")[0].toLowerCase();

            if (path.equals("/api/health")) {
                respond(ctx, buildFullHealth(), HttpResponseStatus.OK);
                return;
            }

            if (path.equals("/api/health/ready")) {
                final Set<ServiceType> connected = pool.connectedTypes();
                final boolean allUp = allExpectedUp(connected);
                respond(ctx,
                        "{\"ready\":" + allUp + ",\"connected\":" + connected.size() + "}",
                        allUp ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
                return;
            }

            // /api/health/{TYPE}
            if (path.startsWith("/api/health/")) {
                final String typeName = path.substring("/api/health/".length()).toUpperCase();
                try {
                    final ServiceType type = ServiceType.of(typeName);
                    final boolean up = pool.isConnected(type);
                    respond(ctx,
                            "{\"service\":\"" + type.name() + "\",\"status\":\"" + (up ? "UP" : "DOWN") + "\"}",
                            up ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
                } catch (IllegalArgumentException e) {
                    respond(ctx, "{\"error\":\"Unknown service type: " + typeName + "\"}",
                            HttpResponseStatus.BAD_REQUEST);
                }
                return;
            }

            respond(ctx, "{\"error\":\"Not found\"}", HttpResponseStatus.NOT_FOUND);
        }

        private void respond(ChannelHandlerContext ctx, String body, HttpResponseStatus status) {
            final byte[] bytes = body.getBytes(CharsetUtil.UTF_8);
            final FullHttpResponse resp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
            resp.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                    .set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(bytes.length))
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    // ── JSON builders ─────────────────────────────────────────────────────────

    private String buildFullHealth() {
        final Set<ServiceType> connected = pool.connectedTypes();
        final Map<ServiceType, Long> timestamps = pool.connectedAtSnapshot();
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
            if (up && timestamps.containsKey(type)) {
                sb.append(",\"connected_at\":").append(timestamps.get(type));
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

    /**
     * "All expected" = at least DATABASE, IA, ANTICHEAT, LOBBY are connected.
     * WEB is optional (dashboard) — doesn't block readiness.
     */
    private static boolean allExpectedUp(final Set<ServiceType> connected) {
        return connected.containsAll(ServiceType.requiredValues());
    }
}
