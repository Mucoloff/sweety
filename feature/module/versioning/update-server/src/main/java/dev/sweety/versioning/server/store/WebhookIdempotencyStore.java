package dev.sweety.versioning.server.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sweety.versioning.server.store.WebhookIdempotencyLog;

import java.util.concurrent.TimeUnit;

public class WebhookIdempotencyStore implements WebhookIdempotencyLog {

    private final Cache<String, Boolean> deliveries;

    public WebhookIdempotencyStore(long ttlMs) {
        this.deliveries = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public boolean isProcessed(String id) {
        return id != null && deliveries.getIfPresent(id) != null;
    }

    @Override
    public void mark(String id) {
        if (id != null) deliveries.put(id, Boolean.TRUE);
    }
}
