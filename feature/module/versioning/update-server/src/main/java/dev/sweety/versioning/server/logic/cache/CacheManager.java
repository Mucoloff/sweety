package dev.sweety.versioning.server.logic.cache;

import dev.sweety.versioning.server.logic.storage.Storage;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    private final EnumMap<Artifact, Path> artifacts;
    private final ConcurrentHashMap<CacheKey, Object> locks = new ConcurrentHashMap<>();

    public CacheManager(Storage storage) {
        this.artifacts = storage.artifacts();
    }

    public byte[] getOrCreate(CacheKey key, CacheProducer producer) throws IOException {
        Path artifactRoot = artifacts.get(key.artifact());
        Path cachedPath = key.toPath(artifactRoot);
        Path tempPath = cachedPath.resolveSibling(cachedPath.getFileName() + ".tmp");

        if (Files.exists(cachedPath)) return Files.readAllBytes(cachedPath);

        Object lock = locks.computeIfAbsent(key, _k -> new Object());
        synchronized (lock) {
            try {
                if (Files.exists(cachedPath)) return Files.readAllBytes(cachedPath);

                byte[] data = producer.produce(key);
                Path dir = cachedPath.getParent();
                if (dir != null) Files.createDirectories(dir);

                Files.write(tempPath, data);
                Files.move(tempPath, cachedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return data;
            } finally {
                locks.remove(key, lock);
            }
        }
    }

}