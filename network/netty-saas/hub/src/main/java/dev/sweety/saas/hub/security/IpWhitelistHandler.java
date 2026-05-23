package dev.sweety.saas.hub.security;

import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dynamic IP whitelist filter for the Hub Netty pipeline.
 * Periodically fetches allowed IPs from all active plans in the database.
 * If the whitelist is empty (no plans have explicit IPs), allows all connections
 * (they'll be validated later by the license handshake).
 * If any plan has explicit IPs, ONLY those IPs can connect.
 *
 * Thread-safe: uses ConcurrentHashMap, updated by scheduled executor.
 */
@ChannelHandler.Sharable
public class IpWhitelistHandler extends ChannelInboundHandlerAdapter {

    private static final SimpleLogger LOG = SimpleLogger.of(IpWhitelistHandler.class);

    private record Snapshot(Set<String> allowedIps, boolean whitelistActive, long lastRefreshMs) {}

    private final AtomicReference<Snapshot> state =
            new AtomicReference<>(new Snapshot(Collections.emptySet(), false, 0L));

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ip-whitelist-refresh");
                t.setDaemon(true);
                return t;
            });

    /**
     * @param ipProvider Function that returns all allowed IPs from the database.
     *                   Implementation: query all active Plans, collect all allowed_ips,
     *                   return as Set<String>.
     * @param refreshIntervalSeconds How often to refresh the whitelist from DB.
     */
    public IpWhitelistHandler(IpProvider ipProvider, int refreshIntervalSeconds) {
        refresh(ipProvider);
        scheduler.scheduleAtFixedRate(
                () -> refresh(ipProvider),
                refreshIntervalSeconds,
                refreshIntervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private void refresh(IpProvider ipProvider) {
        try {
            Set<String> ips = ipProvider.getAllowedIps();
            if (ips != null && !ips.isEmpty()) {
                Set<String> newSet = ConcurrentHashMap.newKeySet();
                newSet.addAll(ips);
                state.set(new Snapshot(newSet, true, System.currentTimeMillis()));
            } else {
                state.set(new Snapshot(Collections.emptySet(), false, System.currentTimeMillis()));
            }
        } catch (Exception e) {
            LOG.error("[IpWhitelist] Failed to refresh: " + e.getMessage());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Snapshot snap = state.get();
        if (!snap.whitelistActive()) {
            super.channelActive(ctx);
            return;
        }
        final InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        final String ip = remoteAddress.getAddress().getHostAddress();
        if (snap.allowedIps().contains(ip)) {
            super.channelActive(ctx);
        } else {
            LOG.warn("[IpWhitelist] BLOCKED connection from " + ip
                    + " (not in whitelist of " + snap.allowedIps().size() + " IPs)");
            ctx.close();
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public int whitelistSize() {
        return state.get().allowedIps().size();
    }

    public boolean isActive() {
        return state.get().whitelistActive();
    }

    public long lastRefreshMs() {
        return state.get().lastRefreshMs();
    }

    public Set<String> allowedIps() {
        return Collections.unmodifiableSet(state.get().allowedIps());
    }

    @FunctionalInterface
    public interface IpProvider {
        Set<String> getAllowedIps();
    }
}
