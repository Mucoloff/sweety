package dev.sweety.versioning.server.cache;

import dev.sweety.versioning.server.storage.Storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    private final Path cacheRoot, tempDir;
    private final ConcurrentHashMap<CacheKey, Object> locks = new ConcurrentHashMap<>();

    public CacheManager(Storage storage) {
        this.cacheRoot = storage.cache();
        this.tempDir = storage.tmp();
    }

    public byte[] getOrCreate(CacheKey key, CacheProducer producer) throws IOException {
        Path cachedPath = key.toPath(cacheRoot);
        Path tempPath = key.toPath(tempDir, "jar.tmp");

        if (Files.exists(cachedPath)) return Files.readAllBytes(cachedPath);

        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
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