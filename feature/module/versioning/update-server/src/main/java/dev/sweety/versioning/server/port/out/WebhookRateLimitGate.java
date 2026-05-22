package dev.sweety.versioning.server.port.out;

public interface WebhookRateLimitGate {
    boolean allow(String ip);
}
