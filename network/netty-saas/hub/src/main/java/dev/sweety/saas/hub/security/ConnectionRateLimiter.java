package dev.sweety.saas.hub.security;

import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP connection rate limiter for the Hub Netty pipeline.
 * Allows at most MAX_CONNECTIONS_PER_WINDOW new connections per IP per cleanup window.
 * Cleanup resets all counters every CLEANUP_INTERVAL_SECONDS (default 5s).
 *
 * Thread-safe: ConcurrentHashMap + AtomicInteger.
 */
@ChannelHandler.Sharable
public class ConnectionRateLimiter extends ChannelInboundHandlerAdapter {

    private static final SimpleLogger LOG = new SimpleLogger(ConnectionRateLimiter.class);

    private static final int MAX_CONNECTIONS_PER_WINDOW = Integer
            .parseInt(System.getenv().getOrDefault("HUB_RATE_LIMIT_MAX_CONN", "10"));
    private static final int CLEANUP_INTERVAL_SECONDS = Integer
            .parseInt(System.getenv().getOrDefault("HUB_RATE_LIMIT_WINDOW_SECONDS", "5"));

    private final ConcurrentHashMap<InetAddress, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hub-rate-limiter-cleanup");
        t.setDaemon(true);
        return t;
    });

    public ConnectionRateLimiter() {
        scheduler.scheduleAtFixedRate(
                connectionCounts::clear,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        LOG.info("[RateLimit] Hub connection rate limiter: max " + MAX_CONNECTIONS_PER_WINDOW
                + " connections per " + CLEANUP_INTERVAL_SECONDS + "s window per IP");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final SocketAddress remote = ctx.channel().remoteAddress();
        if (!(remote instanceof InetSocketAddress inet)) {
            super.channelActive(ctx);
            return;
        }
        final InetAddress addr = inet.getAddress();
        final int count = connectionCounts
                .computeIfAbsent(addr, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (count > MAX_CONNECTIONS_PER_WINDOW) {
            LOG.warn("[RateLimit] BLOCKED " + addr.getHostAddress()
                    + " — " + count + " connections in window (max=" + MAX_CONNECTIONS_PER_WINDOW + ")");
            ctx.close();
            return;
        }
        super.channelActive(ctx);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
