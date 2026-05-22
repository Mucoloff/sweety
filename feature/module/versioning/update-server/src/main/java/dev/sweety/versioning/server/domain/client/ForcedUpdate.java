package dev.sweety.versioning.server.domain.client;

import dev.sweety.time.Expirable;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

public record ForcedUpdate(Channel channel, @Nullable Version prev, Version target, long expireAt) implements Expirable {}
