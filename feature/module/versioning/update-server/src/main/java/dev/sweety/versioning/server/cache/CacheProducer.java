package dev.sweety.versioning.server.cache;

import java.io.IOException;

@FunctionalInterface
public interface CacheProducer {
    byte[] produce() throws IOException;
}