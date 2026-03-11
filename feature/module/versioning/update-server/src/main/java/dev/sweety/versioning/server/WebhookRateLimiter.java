package dev.sweety.versioning.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookRateLimiter {

    private static final int GLOBAL_LIMIT_PER_MINUTE = 1000;
    private static final int PER_IP_LIMIT_PER_MINUTE = 100;
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentHashMap<String, RateWindow> perIpWindows = new ConcurrentHashMap<>();
    private final RateWindow globalWindow = new RateWindow(WINDOW_MS);

    static class RateWindow {
        private final long windowMs;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

        RateWindow(long windowMs) {
            this.windowMs = windowMs;
        }

        boolean tryIncrementAndCheck(int limit) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start > windowMs) {
                windowStart.set(now);
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }

    public boolean allowRequest(String remoteIp) {
        boolean globalOk = globalWindow.tryIncrementAndCheck(GLOBAL_LIMIT_PER_MINUTE);
        if (!globalOk) {
            return false;
        }

        String ip = normalizeIp(remoteIp);
        RateWindow window = perIpWindows.computeIfAbsent(ip, k -> new RateWindow(WINDOW_MS));
        return window.tryIncrementAndCheck(PER_IP_LIMIT_PER_MINUTE);
    }

    private String normalizeIp(String remoteIp) {
        return remoteIp != null && !remoteIp.isBlank() ? remoteIp : "unknown";
    }
}
