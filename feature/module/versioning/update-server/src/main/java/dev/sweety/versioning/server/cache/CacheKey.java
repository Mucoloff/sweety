package dev.sweety.versioning.server.cache;

import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;

import java.util.UUID;

public record CacheKey(Artifact artifact, Version version, UUID clientId) {
}