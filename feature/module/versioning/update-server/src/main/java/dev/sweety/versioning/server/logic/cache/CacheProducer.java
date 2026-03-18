package dev.sweety.versioning.server.logic.cache;

import java.io.IOException;

@FunctionalInterface
public interface CacheProducer {
    byte[] produce(CacheKey key) throws IOException;
}