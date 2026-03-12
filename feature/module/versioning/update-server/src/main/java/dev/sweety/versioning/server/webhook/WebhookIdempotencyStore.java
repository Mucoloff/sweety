package dev.sweety.versioning.server.webhook;

import java.util.concurrent.ConcurrentHashMap;

public class WebhookIdempotencyStore {

    private static final long DEFAULT_TTL = 60 * 60 * 1000;

    private final ConcurrentHashMap<String, Long> deliveries = new ConcurrentHashMap<>();
    private final long ttl;

    public WebhookIdempotencyStore(long ttl) {
        this.ttl = ttl;
    }

    public WebhookIdempotencyStore() {
        this(DEFAULT_TTL);
    }

    public boolean isProcessed(String id) {

        if (id == null) return false;

        Long time = deliveries.get(id);
        if (time == null) return false;

        if (System.currentTimeMillis() - time > ttl) {
            deliveries.remove(id);
            return false;
        }

        return true;
    }

    public void mark(String id) {

        if (id != null) {
            deliveries.put(id, System.currentTimeMillis());
        }

    }

    public void cleanup() {

        long now = System.currentTimeMillis();

        deliveries.entrySet().removeIf(
                e -> now - e.getValue() > ttl
        );

    }

}