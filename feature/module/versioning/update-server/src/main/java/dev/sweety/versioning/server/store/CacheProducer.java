package dev.sweety.versioning.server.store;

import dev.sweety.versioning.server.data.CacheKey;

import java.io.IOException;

@FunctionalInterface
public interface CacheProducer {
    byte[] produce(CacheKey key) throws IOException;
}
