package dev.sweety.netty.service;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-sender hard rate cap on the hub relay: a fixed 1-second sliding window per {@code senderId}, so one
 * misbehaving edge (a buggy retry loop, not necessarily malicious — every sender here already passed the
 * mesh's {@code CONTROL_SECRET} identify gate) can't flood the relay or its targets. Deliberately simpler
 * than an adaptive EWMA/variance detector: the mesh only has a handful of known, trusted edges, so a flat
 * cap is enough defense-in-depth without the extra complexity of anomaly scoring.
 */
final class HubRateGate {

    /** Messages/second allowed from a single senderId before {@link #exceeded} starts dropping. */
    private static final long MAX_PER_SECOND = 5_000L;

    private final Int2ObjectOpenHashMap<Counter> counters = new Int2ObjectOpenHashMap<>();

    /** True if {@code senderId} has exceeded its budget for the current 1s window — caller should drop. */
    synchronized boolean exceeded(int senderId) {
        long now = System.currentTimeMillis();
        Counter c = counters.computeIfAbsent(senderId, id -> new Counter());
        long windowStart = c.windowStart.get();
        if (now - windowStart >= 1000L) {
            c.windowStart.set(now);
            c.count.set(0L);
        }
        return c.count.incrementAndGet() > MAX_PER_SECOND;
    }

    private static final class Counter {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong count = new AtomicLong();
    }
}
