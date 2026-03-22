package dev.sweety.versioning.server.logic.webhook;

import dev.sweety.versioning.server.Settings;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookRateLimiter {

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

            if (now - s > Settings.RATE_LIMIT_WINDOW && start.compareAndSet(s, now)) count.set(0);

            return count.getAndIncrement() < limit;

        }

    }

    public boolean allow(String ip) {
        if (!global.allow(Settings.GLOBAL_RATE_LIMIT)) return false;

        final RateWindow window = this.ip.computeIfAbsent(ip == null ? "unknown" : ip, RateWindow::new);

        return window.allow(Settings.PER_IP_RATE_LIMIT);

    }

}