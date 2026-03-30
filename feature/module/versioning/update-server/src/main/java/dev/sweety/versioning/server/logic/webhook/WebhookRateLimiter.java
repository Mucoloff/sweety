package dev.sweety.versioning.server.logic.webhook;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookRateLimiter {

    private final ConcurrentHashMap<String, RateWindow> ip = new ConcurrentHashMap<>();
    private final RateWindow global;

    private final long rateLimitWindow;
    private final int globalRateLimit, perIpRateLimit;

    public WebhookRateLimiter(long rateLimitWindow, int globalRateLimit, int perIpRateLimit) {
        this.rateLimitWindow = rateLimitWindow;
        this.globalRateLimit = globalRateLimit;
        this.perIpRateLimit = perIpRateLimit;
        this.global = new RateWindow(this.rateLimitWindow);
    }

    static class RateWindow {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicLong start = new AtomicLong(System.nanoTime());
        private final long rateLimitWindow;

        RateWindow(long rateLimitWindow) {
            this.rateLimitWindow = rateLimitWindow;
        }


        boolean allow(int limit) {
            final long now = System.nanoTime();
            final long s = start.get();

            if (now - s > this.rateLimitWindow && start.compareAndSet(s, now)) count.set(0);

            return count.getAndIncrement() < limit;

        }

    }

    public boolean allow(String ip) {
        if (!global.allow(this.globalRateLimit)) return false;

        final RateWindow window = this.ip.computeIfAbsent(ip == null ? "unknown" : ip, _ -> new RateWindow(this.rateLimitWindow));

        return window.allow(this.perIpRateLimit);

    }

}