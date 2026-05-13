package dev.sweety.versioning.server.logic.webhook;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

public class WebhookIdempotencyStore {

    private final Cache<String, Boolean> deliveries;

    public WebhookIdempotencyStore(long ttlMs) {
        this.deliveries = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean isProcessed(String id) {
        return id != null && deliveries.getIfPresent(id) != null;
    }

    public void mark(String id) {
        if (id != null) deliveries.put(id, Boolean.TRUE);
    }
}