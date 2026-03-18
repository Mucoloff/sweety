package dev.sweety.versioning.server.logic.client;

import dev.sweety.versioning.version.channel.Channel;

import java.time.Instant;
import java.util.UUID;

public record ClientProfile(UUID clientId, Channel channel, Instant firstSeen, Instant lastSeen) {
}