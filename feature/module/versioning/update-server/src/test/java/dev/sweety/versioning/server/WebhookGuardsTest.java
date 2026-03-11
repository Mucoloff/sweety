package dev.sweety.versioning.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookGuardsTest {

    @Test
    void idempotencyStoreMarksAndChecksDelivery() {
        WebhookIdempotencyStore store = new WebhookIdempotencyStore(60_000);

        assertFalse(store.isProcessed("delivery-1"));
        store.mark("delivery-1");
        assertTrue(store.isProcessed("delivery-1"));

        assertFalse(store.isProcessed("delivery-2"));
        store.mark("delivery-2");
        assertTrue(store.isProcessed("delivery-2"));
    }

    @Test
    void rateLimiterAllowsRequestsWithinLimit() {
        WebhookRateLimiter limiter = new WebhookRateLimiter();

        assertTrue(limiter.allowRequest("192.168.1.1"));
        assertTrue(limiter.allowRequest("192.168.1.1"));

        assertTrue(limiter.allowRequest("10.0.0.1"));
        assertTrue(limiter.allowRequest("10.0.0.1"));
    }

    @Test
    void rateLimiterRejectsAfterGlobalLimit() {
        WebhookRateLimiter limiter = new WebhookRateLimiter();

        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.allowRequest("192.168." + (i / 256) + "." + (i % 256)));
        }

        assertFalse(limiter.allowRequest("192.168.100.100"));
    }
}

