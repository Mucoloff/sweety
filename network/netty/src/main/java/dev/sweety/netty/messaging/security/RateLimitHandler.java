package dev.sweety.netty.messaging.security;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Netty ChannelHandler that limits inbound packet frequency using a TokenBucket.
 * Supports per-channel (TCP) and per-IP / per-Endpoint (UDP) modes.
 */
@ChannelHandler.Sharable
public final class RateLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitHandler.class);

    public enum Mode {
        PER_CHANNEL,
        PER_IP,
        GLOBAL
    }

    private final Mode mode;
    private final long capacity;
    private final double refillRatePerSec;
    private final TokenBucket globalBucket;
    private final ConcurrentHashMap<InetAddress, TokenBucket> ipBuckets;
    private final Consumer<SocketAddress> dropListener;

    public RateLimitHandler(long capacity, double refillRatePerSec, Mode mode) {
        this(capacity, refillRatePerSec, mode, null);
    }

    public RateLimitHandler(long capacity, double refillRatePerSec, Mode mode, Consumer<SocketAddress> dropListener) {
        this.capacity = capacity;
        this.refillRatePerSec = refillRatePerSec;
        this.mode = mode;
        this.dropListener = dropListener;
        this.globalBucket = (mode == Mode.GLOBAL) ? new TokenBucket(capacity, refillRatePerSec) : null;
        this.ipBuckets = (mode == Mode.PER_IP) ? new ConcurrentHashMap<>() : null;
    }

    public static RateLimitHandler perChannel(long burstCapacity, double packetsPerSecond) {
        return new RateLimitHandler(burstCapacity, packetsPerSecond, Mode.PER_CHANNEL);
    }

    public static RateLimitHandler perIp(long burstCapacity, double packetsPerSecond) {
        return new RateLimitHandler(burstCapacity, packetsPerSecond, Mode.PER_IP);
    }

    public static RateLimitHandler global(long burstCapacity, double packetsPerSecond) {
        return new RateLimitHandler(burstCapacity, packetsPerSecond, Mode.GLOBAL);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TokenBucket bucket = resolveBucket(ctx, msg);

        if (bucket != null && !bucket.tryConsume()) {
            // Drop message immediately to protect server from exhaustion
            SocketAddress remote = extractRemoteAddress(ctx, msg);
            ReferenceCountUtil.safeRelease(msg);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Rate limit exceeded for remote {}, dropping packet.", remote);
            }
            if (dropListener != null) {
                dropListener.accept(remote);
            }
            return;
        }

        ctx.fireChannelRead(msg);
    }

    private static final io.netty.util.AttributeKey<TokenBucket> BUCKET_KEY =
            io.netty.util.AttributeKey.valueOf("SWEETY_RATE_LIMIT_BUCKET");

    private TokenBucket resolveBucket(ChannelHandlerContext ctx, Object msg) {
        switch (mode) {
            case GLOBAL -> {
                return globalBucket;
            }
            case PER_IP -> {
                SocketAddress addr = extractRemoteAddress(ctx, msg);
                if (addr instanceof InetSocketAddress inet) {
                    return ipBuckets.computeIfAbsent(inet.getAddress(),
                            k -> new TokenBucket(capacity, refillRatePerSec));
                }
                return null;
            }
            case PER_CHANNEL -> {
                TokenBucket bucket = ctx.channel().attr(BUCKET_KEY).get();
                if (bucket == null) {
                    bucket = new TokenBucket(capacity, refillRatePerSec);
                    ctx.channel().attr(BUCKET_KEY).set(bucket);
                }
                return bucket;
            }
            default -> {
                return null;
            }
        }
    }

    private SocketAddress extractRemoteAddress(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof DatagramPacket dp) {
            return dp.sender();
        }
        return ctx.channel().remoteAddress();
    }
}
