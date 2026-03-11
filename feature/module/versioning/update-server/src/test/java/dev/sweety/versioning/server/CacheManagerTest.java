package dev.sweety.versioning.server;

import dev.sweety.versioning.server.cache.CacheKey;
import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheManagerTest {

    @Test
    void singleFlightProducerRunsOncePerKey() throws Exception {
        Path tmp = Files.createTempDirectory("cache-test-");
        CacheManager cache = new CacheManager(tmp);
        CacheKey key = new CacheKey(Artifact.APP, new Version(1, 2, 3), UUID.nameUUIDFromBytes("clientA".getBytes(StandardCharsets.UTF_8)));

        AtomicInteger produced = new AtomicInteger(0);
        int workers = 8;
        CountDownLatch latch = new CountDownLatch(workers);

        try (var pool = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                pool.submit(() -> {
                    try {
                        cache.getOrCreate(key, () -> {
                            produced.incrementAndGet();
                            Thread.sleep(80);
                            return "jar-bytes".getBytes(StandardCharsets.UTF_8);
                        });
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        assertEquals(1, produced.get());
        assertTrue(Files.exists(cache.toPath(key)));
    }
}
