package dev.sweety.versioning.server.webhook;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookRateLimiter {

    private static final int GLOBAL_LIMIT = 1000;
    private static final int PER_IP_LIMIT = 100;

    private static final long WINDOW = 60_000_000;

    private final RateWindow global = new RateWindow("global");
    private final ConcurrentHashMap<String, RateWindow> ip = new ConcurrentHashMap<>();

    static class RateWindow {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicLong start = new AtomicLong(System.nanoTime());

        public RateWindow(String ip) {
        }

        boolean allow(int limit) {
            final long now = System.nanoTime();
            final long s = start.get();

            if (now - s > WINDOW && start.compareAndSet(s, now)) count.set(0);

            return count.getAndIncrement() < limit;

        }

    }

    public boolean allow(String ip) {
        if (!global.allow(GLOBAL_LIMIT)) return false;

        final RateWindow window = this.ip.computeIfAbsent(ip == null ? "unknown" : ip, RateWindow::new);

        return window.allow(PER_IP_LIMIT);

    }

}