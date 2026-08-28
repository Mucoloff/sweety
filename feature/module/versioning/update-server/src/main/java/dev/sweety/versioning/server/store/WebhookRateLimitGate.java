package dev.sweety.versioning.server.store;

public interface WebhookRateLimitGate {
    boolean allow(String ip);
}
