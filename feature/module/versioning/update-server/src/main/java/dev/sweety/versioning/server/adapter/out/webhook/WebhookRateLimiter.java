package dev.sweety.versioning.server.adapter.out.webhook;

import dev.sweety.versioning.server.port.out.WebhookRateLimitGate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookRateLimiter implements WebhookRateLimitGate {

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

        private final AtomicLong state = new AtomicLong();
        private final long windowSeconds;

        RateWindow(long rateLimitWindowNanos) {
            this.windowSeconds = TimeUnit.NANOSECONDS.toSeconds(rateLimitWindowNanos);
            this.state.set(pack(System.nanoTime() / 1_000_000_000L, 0));
        }

        boolean allow(int limit) {
            long now = System.nanoTime() / 1_000_000_000L;
            long prev = state.getAndUpdate(s -> {
                long ts = s >>> 32;
                int count = (int) s;
                if (now - ts >= windowSeconds) {
                    return pack(now, 1);
                }
                if (count < limit) {
                    return pack(ts, count + 1);
                }
                return s;
            });

            long prevTs = prev >>> 32;
            int prevCount = (int) prev;

            if (now - prevTs >= windowSeconds) {
                return true;
            }
            return prevCount < limit;
        }

        private static long pack(long ts, int count) {
            return (ts << 32) | (count & 0xFFFFFFFFL);
        }
    }

    @Override
    public boolean allow(String ip) {
        if (!global.allow(this.globalRateLimit)) return false;
        final RateWindow window = this.ip.computeIfAbsent(ip == null ? "unknown" : ip, _ -> new RateWindow(this.rateLimitWindow));
        return window.allow(this.perIpRateLimit);
    }
}
