package dev.sweety.versioning.server.webhook;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookRateLimiter {

    private static final int GLOBAL_LIMIT = 1000;
    private static final int PER_IP_LIMIT = 100;

    private static final long WINDOW = 60_000;

    private final RateWindow global = new RateWindow();
    private final ConcurrentHashMap<String, RateWindow> perIp = new ConcurrentHashMap<>();

    static class RateWindow {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicLong start = new AtomicLong(System.currentTimeMillis());

        boolean allow(int limit) {

            long now = System.currentTimeMillis();
            long s = start.get();

            if (now - s > WINDOW) {

                if (start.compareAndSet(s, now)) {
                    count.set(0);
                }

            }

            return count.incrementAndGet() <= limit;

        }

    }

    public boolean allow(String ip) {

        if (!global.allow(GLOBAL_LIMIT)) {
            return false;
        }

        RateWindow window = perIp.computeIfAbsent(
                ip == null ? "unknown" : ip,
                k -> new RateWindow()
        );

        return window.allow(PER_IP_LIMIT);

    }

}