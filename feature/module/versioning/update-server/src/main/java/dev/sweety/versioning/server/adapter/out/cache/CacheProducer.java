package dev.sweety.versioning.server.adapter.out.cache;

import dev.sweety.versioning.server.domain.cache.CacheKey;

import java.io.IOException;

@FunctionalInterface
public interface CacheProducer {
    byte[] produce(CacheKey key) throws IOException;
}
