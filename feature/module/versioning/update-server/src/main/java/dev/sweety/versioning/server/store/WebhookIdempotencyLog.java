package dev.sweety.versioning.server.store;

public interface WebhookIdempotencyLog {
    boolean isProcessed(String deliveryId);
    void mark(String deliveryId);
}
