package dev.sweety.versioning.server.port.out;

public interface WebhookIdempotencyLog {
    boolean isProcessed(String deliveryId);
    void mark(String deliveryId);
}
