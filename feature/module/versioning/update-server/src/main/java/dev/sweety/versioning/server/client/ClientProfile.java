package dev.sweety.versioning.server.client;

import java.time.Instant;
import java.util.UUID;

public record ClientProfile(UUID clientId, String channel, Instant firstSeen, Instant lastSeen) {
}