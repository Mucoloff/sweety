package dev.sweety.versioning.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookIdempotencyStore {

    private static final long DEFAULT_TTL_MS = 3600_000;

    private final ConcurrentHashMap<String, Long> deliveryIds = new ConcurrentHashMap<>();
    private final long ttlMs;

    public WebhookIdempotencyStore(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public WebhookIdempotencyStore() {
        this(DEFAULT_TTL_MS);
    }

    public boolean isProcessed(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return false;
        }
        Long timestamp = deliveryIds.get(deliveryId);
        if (timestamp == null) {
            return false;
        }
        long age = System.currentTimeMillis() - timestamp;
        if (age > ttlMs) {
            deliveryIds.remove(deliveryId);
            return false;
        }
        return true;
    }

    public void mark(String deliveryId) {
        if (deliveryId != null && !deliveryId.isBlank()) {
            deliveryIds.put(deliveryId, System.currentTimeMillis());
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        deliveryIds.entrySet().removeIf(e -> (now - e.getValue()) > ttlMs);
    }
}

