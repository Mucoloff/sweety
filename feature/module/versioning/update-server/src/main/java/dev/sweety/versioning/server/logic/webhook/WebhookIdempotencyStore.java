package dev.sweety.versioning.server.logic.webhook;

import java.util.concurrent.ConcurrentHashMap;

public class WebhookIdempotencyStore {

    private final ConcurrentHashMap<String, Long> deliveries = new ConcurrentHashMap<>();
    private final long ttl;

    public WebhookIdempotencyStore(long ttl) {
        this.ttl = ttl;
    }

    public boolean isProcessed(String id) {
        lazyCleanup();
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
        if (id != null) deliveries.put(id, System.currentTimeMillis());
    }

    private void lazyCleanup() {
        if (deliveries.size() > 1000) cleanup();
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        deliveries.entrySet().removeIf(e -> now - e.getValue() > ttl);
    }

}