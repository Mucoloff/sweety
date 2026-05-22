package dev.sweety.versioning.server.domain.client;

import dev.sweety.versioning.version.channel.Channel;

import java.util.UUID;

public record ClientInfo(UUID id, Channel channel) {}
